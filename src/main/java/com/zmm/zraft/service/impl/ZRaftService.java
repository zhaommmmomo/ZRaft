package com.zmm.zraft.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
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
 * @author zmm
 * @date 2021/11/16 17:18
 */
public class ZRaftService extends IZRaftServiceGrpc.IZRaftServiceImplBase implements INodeService {

    private static List<IZRaftServiceGrpc.IZRaftServiceFutureStub> rpcFutureMethod;

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

    @Override
    public void appendEntries(AppendRequest request,
                              StreamObserver<ZRaftResponse> responseObserver) {

        ZRaftResponse.Builder builder = ZRaftResponse.newBuilder()
                .setTerm(NodeManager.node.getCurrentTerm());
        if (request.getTerm() < NodeManager.node.getCurrentTerm()) {
            builder.setSuccess(false);
        }

        // 更新等待定时器的时间
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());

        String flag = request.getEntries(0);
        if ("".equals(flag)) {
            // 说明这是一个心跳包
        } else {

        }

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

    // TODO: 2021/11/17 发送逻辑需要改一下
    @Override
    public void sendVoteRequest() {
        NodeManager.printLog("============sendVoteRequest============");

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
        switch (NodeManager.node.getNodeState()) {
            case FOLLOWER:
                NodeManager.printLog("to be candidate......");
                // 1. 将当前节点设置设置为Candidate并为自己投票
                startNewTerm();
                // 2. 向其他节点发送RPC请求投票
                sendVoteRequest();
                break;
            case CANDIDATE:
                NodeManager.printLog("to be Leader......");

                break;

            default: break;
        }
    }

    @Override
    public void levelDown() {

    }

    /**
     * 判断当前节点是否投票给候选人
     * 如果候选人的term < currentTerm，不给该候选人投票
     * 如果当前节点没有投票或者投给了候选人并且候选人日志和当前节点一样新，就给该候选人投票
     * @param request       候选人id
     * @return              true / false
     */
    private boolean vote(VoteRequest request) {
        long votedFor;
        return  request.getTerm() >= NodeManager.node.getCurrentTerm() &&
                ((votedFor = NodeManager.node.getVotedFor()) == 0 ||
                (votedFor == request.getCandidateId() &&
                NodeManager.node.getLogIndex() == request.getLastLogIndex() &&
                NodeManager.node.getLastLogTerm() == request.getLastLogTerm()));
    }

    /**
     * 开始新的任期，当等待时间超时，节点由Follower变为Candidate时调用
     */
    private void startNewTerm() {
        NodeManager.node.addTerm();
        NodeManager.node.setNodeState(Node.NodeState.CANDIDATE);
        NodeManager.node.setVotedFor(NodeManager.node.getId());
        NodeManager.node.setLeaderId(0);
        FutureListener.resetVoteCount();
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
