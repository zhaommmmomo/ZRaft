package com.zmm.zraft.service.impl;

import com.zmm.zraft.Node;
import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.IZRaftServiceGrpc;
import com.zmm.zraft.gRpc.VoteRequest;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.INodeService;
import io.grpc.stub.StreamObserver;

/**
 * @author zmm
 * @date 2021/11/16 17:18
 */
public class ZRaftService extends IZRaftServiceGrpc.IZRaftServiceImplBase implements INodeService {

    @Override
    public void requestVote(VoteRequest request,
                            StreamObserver<ZRaftResponse> responseObserver) {
        // 更新等待定时器的时间
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());


        boolean vote;
        // 获取当前节点任期
        long currentTerm = NodeManager.node.getCurrentTerm();
        // 获取Candidate节点任期
        long term = request.getTerm();

        ZRaftResponse.Builder builder = ZRaftResponse.newBuilder()
                                                     .setTerm(currentTerm);
        // 获取当前节点状态
        Node.NodeState state = NodeManager.node.getNodeState();
        // 判断Candidate的任期是否小于当前节点
        if (term < currentTerm) {
            // 如果Candidate的任期小于当前节点
            // 不给Candidate投票
            builder.setSuccess(false);
        } else if (term > currentTerm){
            // 如果Candidate任期大于当前节点
            // 直接同意
        } else {
            // 如果Candidate任期等于当前节点
            // 有两种情况：
            //   1. Follower和Candidate收到
            //   2. Leader收到
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void appendEntries(AppendRequest request,
                              StreamObserver<ZRaftResponse> responseObserver) {
        // 更新等待定时器的时间
        NodeManager.electionListener
                .updatePreHeartTime(System.currentTimeMillis());
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
}
