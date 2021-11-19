package com.zmm.zraft.service.impl;

import com.zmm.zraft.Node;
import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.RPCServiceGrpc;
import com.zmm.zraft.gRpc.VoteRequest;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.IZRaftService;
import io.grpc.stub.StreamObserver;

/**
 * RPC方法类
 *
 * 选举超时后，Follower成为Candidate并开始新的选举任期（term），
 * 为自己投票并向其他节点发送请求投票消息，如果接收节点在这个term内
 * 还没有投票，那么它会投票给Candidate并且重置自身节点的选举超时。
 * 一旦Candidate获得多数票，他就会成为Leader，Leader开始向
 * 其他追随者发送追加条目消息，这些消息按心跳超时指定的时间间隔发送。
 * 追随者然后响应每个附加条目（Append Entries）消息，这个term
 * 持续到Candidate停止接收心跳并成为候选人。
 *
 * 如果产生分裂投票，随机设置超时后重新开始选举
 * @author zmm
 * @date 2021/11/16 17:18
 */
public class ZRaftRPCService extends RPCServiceGrpc.RPCServiceImplBase {

    private final IZRaftService zRaftService = new ZRaftService();

    /**
     * 节点选举
     * 选举超时：Follower等待成为Leader的时间，随机设置在150ms ~ 300ms
     * @param request           {
     *                              term:           候选人任期号
     *                              candidateId:    候选人Id
     *                              lastLogIndex:   候选人最好的日志条目索引值
     *                              lastLogTerm:    候选人最后日志条目的任期号
     *                          }
     * ZRaftResponse            {
     *                              "term": 当前任期号
     *                              "voteGranted": true / false
     *                                              是否被投票
     *                          }
     */
    @Override
    public void requestVote(VoteRequest request,
                            StreamObserver<ZRaftResponse> responseObserver) {
        // 当节点收到比自己大的任期，会将自己的任期设置为相同的，然后直接投票
        // 当节点收到和自己一样大的任期，会看自己是否已经投票来判断

        // 更新等待定时器的时间
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());


        ZRaftResponse response = ZRaftResponse.newBuilder()
                                        .setTerm(NodeManager.node.getCurrentTerm())
                                        .setSuccess(vote(request))
                                        .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * 追加条目，心跳，节点间数据的同步，日志复制
     * 1. Leader接收到数据更改，将更改添加到节点日志中（不提交）
     * 2. 将该条目复制到Follower，等待回复，直到大多数（n / 2 + 1）
     *    节点响应成功。如果没有超过半数的节点响应成功，隔段超时时间后重新发送
     * 3. Leader提交数据，然后将结果返回给并通知Follower进行提交
     * @param request           {
     *                              term:           Leader任期
     *                              leaderId:       有时候可能是Candidate收到请求，
     *                                              需要将请求重定向到Leader去
     *                              preLogIndex:    前一个日志条目的索引
     *                              preLogTerm:     前一个日志条目的任期
     *                              entries:        需要被保存的日志条目（如果为空，代表是心跳）
     *                              leaderCommit:   Leader已提交的最高日志条目的索引
     *                          }
     * ZRaftResponse            {
     *                              "term": 当前任期
     *                              "success": true / false。如果Candidate
     *                              所含有的条目和prevLogIndex以及preLogTerm
     *                              匹配上，则为true。
     *                          }
     */
    @Override
    public void appendEntries(AppendRequest request,
                              StreamObserver<ZRaftResponse> responseObserver) {
        // 更新等待计时器
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());

        ZRaftResponse.Builder builder = ZRaftResponse.newBuilder()
                .setTerm(NodeManager.node.getCurrentTerm());

        long term = request.getTerm();
        long currentTerm = NodeManager.node.getCurrentTerm();
        Node.NodeState state = NodeManager.node.getNodeState();

        if (term < currentTerm) {
            // 如果当前任期大于请求节点的任期
            builder.setSuccess(false);

            //if (state == Node.NodeState.LEADER) {
            //    // 如果当前节点是Leader，不用管
            //}

        } else {
            // 如果当前任期小于等于请求节点的任期
            builder.setSuccess(true);
            System.out.println("receive heart...");

            // 执行降级逻辑，六种情况:
            // 1. term == currentTerm && state == Follower。
            //    更新等待时间和数据
            // 2. term == currentTerm && state == Candidate。
            //    当前节点选举输了，更新等待时间、任期信息和数据
            // 3. term == currentTerm && state == Leader。
            //    不可能出现
            // 4. term > currentTerm && state == Follower。
            //    不可能出现
            // 5. term > currentTerm && state == Candidate。
            //    更新等待时间、任期信息和数据
            // 6. term > currentTerm && state == Leader。
            //    更新任期信息和数据，关闭心跳，
            //    执行降级逻辑，
            //levelDown(request);

            // test。只修改信息
            if (term != currentTerm ||
                    state == Node.NodeState.CANDIDATE ||
                    state == Node.NodeState.LEADER) {
                zRaftService.updateNodeTermInfo(request);
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    /**
     * 判断当前节点是否投票给候选人
     * 如果候选人的term < currentTerm，不给该候选人投票
     * 如果当前节点没有投票或者投给了候选人并且候选人日志和当前节点一样新，就给该候选人投票
     * @param request       候选人id
     * @return              true / false
     */
    private synchronized boolean vote(VoteRequest request) {
        long term = request.getTerm();
        long currentTerm = NodeManager.node.getCurrentTerm();
        if (term < currentTerm) {
            return false;
        }

        if (term > currentTerm) {
            // 修改任期
            zRaftService.updateNodeTermInfo(request);
            return true;
        }

        long votedFor = NodeManager.node.getVotedFor();
        long candidateId = request.getCandidateId();
        if (votedFor == 0 ||
           (votedFor == candidateId &&
           NodeManager.node.getLogIndex() == request.getLastLogIndex() &&
           NodeManager.node.getLastLogTerm() == request.getLastLogTerm())) {

            NodeManager.node.setLeaderId(0);
            NodeManager.node.setVotedFor(candidateId);
            NodeManager.printNodeLog();
            return true;
        }

        return  false;
    }
}
