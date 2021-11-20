package com.zmm.zraft.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ProtocolStringList;
import com.zmm.zraft.Node;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.RPCServiceGrpc;
import com.zmm.zraft.gRpc.VoteRequest;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.listen.FutureListener;
import com.zmm.zraft.service.IZRaftService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 基本方法类
 * @author zmm
 * @date 2021/11/19 17:23
 */
public class ZRaftService implements IZRaftService {

    /**
     * RPC异步方法
     */
    public static List<RPCServiceGrpc.RPCServiceFutureStub> rpcFutureMethod;

    @Override
    public void sendVoteRequest() {
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
            RPCServiceGrpc.RPCServiceFutureStub futureStub =
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

    // TODO: 2021/11/18 未完善appendEntries接收方法
    @Override
    public void sendAppendEntries(AppendRequest appendRequest) {
        if (rpcFutureMethod == null) {
            rpcMethodInit();
        }

        int l = rpcFutureMethod.size();
        boolean heart = appendRequest.getEntriesCount() == 0;

        // 本次的结果链表
        List<ListenableFuture<ZRaftResponse>> queue = new ArrayList<>();

        for (int i = 0; i < l; i++) {
            RPCServiceGrpc.RPCServiceFutureStub futureStub =
                    rpcFutureMethod.get(i);

            ListenableFuture<ZRaftResponse> future =
                    futureStub.appendEntries(appendRequest);

            if (!heart) {
                // 如果不是心跳包
                // 添加监听事件，方便消息重发
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
    }

    @Override
    public synchronized void levelUp() {
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
        NodeManager.printNodeLog();
    }

    @Override
    public synchronized void levelDown(AppendRequest request) {
        Node.NodeState state = NodeManager.node.getNodeState();

        if (state == Node.NodeState.LEADER) {
            NodeManager.printLog("Leader level down......");
            // 关闭心跳计时器
            NodeManager.heartListener.stop();
        } else {
            NodeManager.printLog("Candidate level down......");
        }
        // 更新节点任期信息
        updateNodeTermInfo(request);

        // (开启 / 重置)等待计时器。
        // 如果计时器已经开启了，会重置上一个心跳时间和等待时间
        NodeManager.electionListener.start();

        NodeManager.printNodeLog();
    }

    /**
     * 修改节点任期信息
     * @param request       请求数据
     */
    @Override
    public synchronized void updateNodeTermInfo(AppendRequest request) {
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
    @Override
    public synchronized void updateNodeTermInfo(VoteRequest request) {

        NodeManager.node.setTermNum(request.getTerm());
        NodeManager.node.setLeaderId(0);
        NodeManager.node.setVotedFor(request.getCandidateId());

        NodeManager.printNodeLog();
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
            rpcFutureMethod.add(RPCServiceGrpc.newFutureStub(channel));
        }
    }
}
