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
     * 发送添加条目
     */
    void sendAppendEntries(AppendRequest appendRequest);

    /**
     * 晋升方法
     * Follower -> Candidate
     * Candidate -> Leader
     */
    void levelUp();

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
