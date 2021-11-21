package com.zmm.zraft.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.zmm.zraft.Node;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.*;
import com.zmm.zraft.listen.AppendFutureListener;
import com.zmm.zraft.listen.ElectionListener;
import com.zmm.zraft.listen.VoteFutureListener;
import com.zmm.zraft.service.IZRaftService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
            throw new RuntimeException("rpcFutureMethod not init...");
        }

        int l = rpcFutureMethod.size();
        for (int i = 0; i < l; i++) {
            RPCServiceGrpc.RPCServiceFutureStub futureStub =
                    rpcFutureMethod.get(i);
            // 发送RPC请求
            ListenableFuture<ZRaftResponse> future =
                    futureStub.requestVote(voteRequest);
            // 将Future结果添加到链表中
            VoteFutureListener.addFuture(future);
            // 给每个Future设置监听任务
            future.addListener(new VoteFutureListener(),
                    Executors.newFixedThreadPool(l));
        }
        NodeManager.electionListener.updatePreHeartTime(System.currentTimeMillis());
    }

    @Override
    public void sendAppendEntries(int type) {
        if (rpcFutureMethod == null) {
            throw new RuntimeException("rpcFutureMethod not init...");
        }

        int size = ZRaftService.rpcFutureMethod.size();

        if (type != 0) {
            AppendFutureListener.clear();
        }

        for (int i = 0; i < size; i++) {

            AppendRequest.Builder appendBuilder = AppendRequest.newBuilder()
                    .setTerm(NodeManager.node.getCurrentTerm())
                    .setLeaderId(NodeManager.node.getId())
                    .setLeaderCommit(NodeManager.node.getCommitIndex());

            // 获取需要发送的一下个条目的索引
            int nextIndex = NodeManager.nextIndex.get(i);

            // 获取需要给该节点发送的entries
            List<Entry> entries = NodeManager.node.getEntriesFromIndex(nextIndex);

            appendBuilder.setPreLogIndex(nextIndex)
                    .setPreLogTerm(NodeManager.node.getPreTermByIndex(nextIndex))
                    .addAllEntries(entries);


            RPCServiceGrpc.RPCServiceFutureStub futureStub =
                    ZRaftService.rpcFutureMethod.get(i);

            // 调用AppendEntries方法
            ListenableFuture<ZRaftResponse> future =
                    futureStub.appendEntries(appendBuilder.build());

            if (type != 0) {
                // 将结果添加到AppendFutureListener中
                AppendFutureListener.addFuture(future);

                future.addListener(new AppendFutureListener(),
                        Executors.newFixedThreadPool(1));
            }

            // TODO: 2021/11/21 当使用心跳来进行重发，会导致一直给节点发送，
            //  需要接收true然后将nextIndex修改
        }

        // 开启返回
        if (type != 0) {
            AppendFutureListener.response();
        }
    }

    @Override
    public synchronized void sendHeart(AppendRequest appendRequest) {
        if (rpcFutureMethod == null) {
            throw new RuntimeException("rpcFutureMethod not init...");
        }

        int l = rpcFutureMethod.size();
        for (RPCServiceGrpc.RPCServiceFutureStub futureStub : rpcFutureMethod) {
            futureStub.appendEntries(appendRequest);
        }
    }

    @Override
    public synchronized void toBeLeader() {

        NodeManager.printLog("to be Leader......");
        // 修改状态
        NodeManager.node.setNodeState(Node.NodeState.LEADER);
        // 设置当前任期的LeaderId
        NodeManager.node.setLeaderId(NodeManager.node.getId());
        // 修改nextIndex，一致性检测
        synchronized (NodeManager.nextIndex) {
            int l  = NodeManager.nextIndex.size();
            long logIndex = NodeManager.node.getLogIndex();
            for (int i = 0; i < l; i++) {
                NodeManager.nextIndex.set(i, (int) logIndex);
            }
        }
        // 开启心跳，关闭等待超时器
        NodeManager.heartListener.start();
        NodeManager.electionListener.stop();
    }

    @Override
    public synchronized void toBeCandidate() {
        NodeManager.printLog("to be candidate......");
        // 1. 将当前节点设置设置为Candidate并为自己投票
        startNewTerm();
        // 2. 向其他节点发送RPC请求投票
        sendVoteRequest();
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
    private synchronized void startNewTerm() {
        // 增加任期
        NodeManager.node.addTerm();
        // 修改节点状态
        NodeManager.node.setNodeState(Node.NodeState.CANDIDATE);
        // 给自己投票
        NodeManager.node.setVotedFor(NodeManager.node.getId());
        VoteFutureListener.resetVoteCount();
        // 重置当前任期LeaderId
        NodeManager.node.setLeaderId(0);
        // 重置等待超时器
        NodeManager.electionListener.updatePreHeartTime(System.currentTimeMillis());
    }
}
