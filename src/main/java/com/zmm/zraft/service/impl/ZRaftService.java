package com.zmm.zraft.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ProtocolStringList;
import com.zmm.zraft.Node;
import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.IZRaftServiceGrpc;
import com.zmm.zraft.gRpc.VoteRequest;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.INodeService;
import com.zmm.zraft.listen.FutureListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
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
public class ZRaftService extends IZRaftServiceGrpc.IZRaftServiceImplBase implements INodeService {

    private static List<IZRaftServiceGrpc.IZRaftServiceFutureStub> rpcFutureMethod;

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


        //boolean vote;
        //// 获取当前节点任期
        //long currentTerm = NodeManager.node.getCurrentTerm();
        //// 获取Candidate节点任期
        //long term = request.getTerm();
        //
        //ZRaftResponse.Builder builder = ZRaftResponse.newBuilder()
        //                                             .setTerm(currentTerm);
        //// 获取当前节点状态
        //Node.NodeState state = NodeManager.node.getNodeState();
        //// 判断Candidate的任期是否小于当前节点
        //if (term < currentTerm) {
        //    // 如果Candidate的任期小于当前节点
        //    // 不给Candidate投票
        //    builder.setSuccess(false);
        //} else if (term > currentTerm){
        //    // 如果Candidate任期大于当前节点
        //    // 直接同意
        //} else {
        //    // 如果Candidate任期等于当前节点
        //    // 有两种情况：
        //    //   1. Follower和Candidate收到
        //    //   2. Leader收到
        //}

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
                updateNodeTermInfo(request);
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    /**
     * 开始选举
     */
    @Override
    public void startElection() {
        // 1. 将当前节点设置设置为Candidate并为自己投票
        startNewTerm();

        // 2. 向其他节点发送RPC请求投票
        sendVoteRequest();
    }

    @Override
    public void sendVoteRequest() {
        NodeManager.printLog("sendVoteRequest......");

        // 构建请求投票包
        VoteRequest voteRequest = VoteRequest.newBuilder()
                .setTerm(NodeManager.node.getCurrentTerm())
                .setCandidateId(NodeManager.node.getId())
                .setLastLogIndex(NodeManager.node.getLogIndex())
                .setLastLogTerm(NodeManager.node.getLastLogTerm())
                .build();

        if (rpcFutureMethod == null) {
            rpcMethodInit();
        }

        int l = rpcFutureMethod.size();
        for (int i = 0; i < l; i++) {
            IZRaftServiceGrpc.IZRaftServiceFutureStub futureStub =
                    rpcFutureMethod.get(i);
            // 发送RPC请求
            ListenableFuture<ZRaftResponse> future =
                    futureStub.requestVote(voteRequest);
            // 将Future结果添加到链表中
            FutureListener.addFuture(future);
            // 给每个Future设置监听任务
            future.addListener(new FutureListener(),
                    Executors.newFixedThreadPool(l));
        }
    }

    // TODO: 2021/11/18 未实现appendEntries接收方法
    @Override
    public void sendAppendEntries(AppendRequest appendRequest) {
        if (rpcFutureMethod == null) {
            rpcMethodInit();
        }
        int l = rpcFutureMethod.size();
        List<ListenableFuture<ZRaftResponse>> queue = new ArrayList<>(l);
        for (int i = 0; i < l; i++) {
            IZRaftServiceGrpc.IZRaftServiceFutureStub futureStub =
                    rpcFutureMethod.get(i);

            ListenableFuture<ZRaftResponse> future =
                    futureStub.appendEntries(appendRequest);
            queue.add(future);
            future.addListener(new Runnable() {
                @Override
                public void run() {
                    synchronized (queue) {
                        int n = queue.size();
                        for (int j = 0; j < n; j++) {
                            ListenableFuture<ZRaftResponse> response = queue.get(j);
                            if (response.isDone()) {
                                // 记录AppendEntries的结果
                            }
                        }
                    }
                }
            }, Executors.newFixedThreadPool(l));
        }
    }

    @Override
    public void levelUp() {
        Node.NodeState state = NodeManager.node.getNodeState();
        if (state == Node.NodeState.FOLLOWER) {
            NodeManager.printLog("to be candidate......");
            // 1. 将当前节点设置设置为Candidate并为自己投票
            startNewTerm();
            // 2. 向其他节点发送RPC请求投票
            sendVoteRequest();

        } else if (state == Node.NodeState.CANDIDATE) {
            NodeManager.printLog("to be Leader......");
            // 修改状态
            NodeManager.node.setNodeState(Node.NodeState.LEADER);
            // 设置当前任期的LeaderId
            NodeManager.node.setLeaderId(NodeManager.node.getId());
            // 开启心跳，关闭等待超时器
            NodeManager.heartListener.start();
            NodeManager.electionListener.stop();
        }
    }

    @Override
    public void levelDown(AppendRequest request) {
        Node.NodeState state = NodeManager.node.getNodeState();
        if (state == Node.NodeState.FOLLOWER) {
            updateEntries(request.getEntriesList());
            return;
        }

        if (state == Node.NodeState.LEADER) {
            NodeManager.printLog("Leader level down......");
            // 关闭心跳计时器、更新数据并开启等待计时器
            NodeManager.heartListener.stop();
        } else {
            NodeManager.printLog("Candidate level down......");
        }
        // 更新节点任期信息
        updateNodeTermInfo(request);
        // 更新条目
        updateEntries(request.getEntriesList());
        // 开启等待计时器，如果计时器以及开启了，会重置上一个心跳时间和等待时间
        NodeManager.electionListener.start();
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
            updateNodeTermInfo(request);
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

    /**
     * 开始新的任期，当等待时间超时，节点由Follower变为Candidate时调用
     */
    private void startNewTerm() {
        // 增加任期
        NodeManager.node.addTerm();
        // 修改节点状态
        NodeManager.node.setNodeState(Node.NodeState.CANDIDATE);
        // 给自己投票
        NodeManager.node.setVotedFor(NodeManager.node.getId());
        FutureListener.resetVoteCount();
        // 重置当前任期LeaderId
        NodeManager.node.setLeaderId(0);
        // 重置等待超时器
        NodeManager.electionListener.updatePreHeartTime(System.currentTimeMillis());
    }

    /**
     * 修改节点任期信息
     * @param request       请求数据
     */
    private void updateNodeTermInfo(AppendRequest request) {
        // TODO: 2021/11/18 还未实现修改节点的任期信息与Leader同步
        long leaderId = request.getLeaderId();
        NodeManager.node.setTermNum(request.getTerm());
        NodeManager.node.setLeaderId(request.getLeaderId());
        NodeManager.node.setVotedFor(leaderId);
        NodeManager.node.setNodeState(Node.NodeState.FOLLOWER);

        NodeManager.printNodeLog();
    }

    /**
     * 修改节点任期信息
     * @param request       请求数据
     */
    private void updateNodeTermInfo(VoteRequest request) {
        // TODO: 2021/11/18 还未实现修改节点的任期信息与Leader同步
        NodeManager.node.setTermNum(request.getTerm());
        NodeManager.node.setLeaderId(0);
        NodeManager.node.setVotedFor(request.getCandidateId());

        NodeManager.printNodeLog();
    }

    /**
     * 更新条目信息
     * @param entries       新增的条目数据
     */
    private void updateEntries(ProtocolStringList entries) {
        // TODO: 2021/11/18 还未实现新增条目信息
    }

    /**
     * 只有第一次发送RPC请求时会调用
     * 初始化RPC方法并保存到list里面
     */
    private void rpcMethodInit() {
        rpcFutureMethod = new ArrayList<>();
        int l = NodeManager.otherNodes.size();
        NodeManager.allNodeCounts += l;
        for (int i = 0; i < l; i++) {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("127.0.0.1",
                            NodeManager.otherNodes.get(i))
                    .usePlaintext().build();
            rpcFutureMethod.add(IZRaftServiceGrpc.newFutureStub(channel));
        }
    }
}
