package com.zmm.zraft.service;

import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.VoteRequest;

/**
 * @author zmm
 * @date 2021/11/16 9:50
 */
public interface IZRaftService {

    /**
     * 发送选举请求
     */
    void sendVoteRequest();

    /**
     * 给每个节点发送相同的条目
     */
    void sendHeart(AppendRequest appendRequest);

    /**
     * 给每个节点发送不同的条目
     * type:0 表示心跳
     */
    void sendAppendEntries(int type);

    /**
     * 晋升方法
     * Candidate -> Leader
     */
    void toBeLeader();

    /**
     * 晋升方法
     * Follower -> Candidate
     */
    void toBeCandidate();

    /**
     * 降级方法
     * Leader / Candidate  ->  Follower
     * @param request               请求数据
     */
    void levelDown(AppendRequest request);

    /**
     * 修改当前节点任期信息
     * @param request               投票数据
     */
    void updateNodeTermInfo(VoteRequest request);

    /**
     * 修改当前节点任期信息
     * @param request               新增条目数据
     */
    void updateNodeTermInfo(AppendRequest request);
}
