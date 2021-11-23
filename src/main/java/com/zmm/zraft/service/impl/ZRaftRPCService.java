package com.zmm.zraft.service.impl;

import com.google.protobuf.ProtocolStringList;
import com.zmm.zraft.Node;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.*;
import com.zmm.zraft.listen.AppendFutureListener;
import com.zmm.zraft.service.IZRaftService;
import io.grpc.stub.StreamObserver;

import java.util.*;

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
     *                              "term":         当前任期号
     *                              "voteGranted":  true / false
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
     *                              "term":         当前任期
     *                              "success":      true / false。如果Candidate
     *                                              所含有的条目和prevLogIndex以及preLogTerm
     *                                              匹配上，则为true。
     *                          }
     */
    @Override
    public void appendEntries(AppendRequest request,
                              StreamObserver<ZRaftResponse> responseObserver) {

        NodeManager.printLog("appendEntries...");

        ZRaftResponse.Builder builder = ZRaftResponse.newBuilder()
                .setTerm(NodeManager.node.getCurrentTerm());

        // 如果currentTerm > term
        long term = request.getTerm();
        long currentTerm = NodeManager.node.getCurrentTerm();
        if (term < currentTerm) {
            // 返回false
            builder.setSuccess(false);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        }

        // 更新等待计时器
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());

        // 如果term > currentTerm 或者当前节点状态是Candidate
        Node.NodeState state = NodeManager.node.getNodeState();
        if (term > currentTerm || state == Node.NodeState.CANDIDATE) {
            // 修改任期状态并切换为Follower
            zRaftService.levelDown(request);
        } else {
            // 设置LeaderId
            long leaderId = NodeManager.node.getLeaderId();
            if (leaderId == 0) {
                NodeManager.node.setLeaderId(request.getLeaderId());
                NodeManager.node.setNodeState(Node.NodeState.FOLLOWER);
                NodeManager.printNodeInfo();
            }
        }

        long preLogTerm = request.getPreLogTerm();
        long preLogIndex = request.getPreLogIndex();

        // 如果Leader日志索引不能在当前节点的索引上找到
        if (!NodeManager.node.entryIsExist(preLogTerm, preLogIndex)) {
            // 返回false
            builder.setSuccess(false);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        }

        boolean b = true;

        // 如果不是心跳包
        List<Entry> entries = request.getEntriesList();
        if (entries.size() != 0) {
            // 添加日志条目
            b = NodeManager.node.addLogEntries(preLogIndex, entries);
            NodeManager.printLog(NodeManager.node.toString());
        }

        // 判断是否要提交条目
        long leaderCommit = request.getLeaderCommit();
        long commitIndex = NodeManager.node.getCommitIndex();
        if (leaderCommit > commitIndex) {
            // 将提交
            b = NodeManager.node.commitLog(leaderCommit) && b;
        }

        builder.setSuccess(b);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    /**
     * 客户端调用的RPC方法。
     * 如果当前节点是Leader:
     *    第一阶段，将指令保存在log条目中，给其他节点发送AppendEntries，异步等待消息。
     *    第二阶段，当大多数节点返回true，在本地进行提交并将结果返回给用户，同时向其他节点
     *    发送提交命令.
     * 如果当前节点是Follower:
     *    将该请求重定向到Leader去。
     * @param request           指令集（字符串list）
     */
    @Override
    public void sendCommand(Command request, StreamObserver<ClientResponse> responseObserver) {
        ProtocolStringList commandList = request.getCommandList();
        ClientResponse.Builder builder = ClientResponse.newBuilder();
        boolean b = false;
        int size = commandList.size();
        Node.NodeState state = NodeManager.node.getNodeState();
        long leaderId = NodeManager.node.getLeaderId();
        if (size == 0 || state != Node.NodeState.LEADER) {
            responseObserver.onNext(builder.setSuccess(b).setLeaderId(leaderId).build());
            responseObserver.onCompleted();
            return;
        }

        // 处理该请求
        // 第一阶段，保存指令到本地并给其他节点发送消息
        long currentTerm = NodeManager.node.getCurrentTerm();
        List<Entry> entries = new ArrayList<>();
        for (String command : commandList) {
            Entry entry = Entry.newBuilder()
                               .setTerm(currentTerm)
                               .setCommand(command)
                               .build();
            entries.add(entry);
        }
        if (!NodeManager.node.addLogEntries(entries)) {
            // 如果本地添加条目失败，返回false
            responseObserver.onNext(builder.setSuccess(b).setLeaderId(leaderId).build());
            responseObserver.onCompleted();
            return;
        }

        // 将返回交给AppendFutureListener
        AppendFutureListener.responseObserver = responseObserver;

        // 发送RPC请求
        zRaftService.sendAppendEntries(1);
    }

    /**
     * 判断当前节点是否投票给候选人
     * 如果候选人的term < currentTerm，不给该候选人投票
     * 如果当前节点没有投票或者投给了候选人并且候选人日志和当前节点一样新，就给该候选人投票
     * @param request           候选人id
     * @return                  true / false
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
            NodeManager.printNodeInfo();
            return true;
        }

        return  false;
    }
}
