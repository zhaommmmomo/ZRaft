package com.zmm.zraft.service;

import com.zmm.zraft.gRpc.AppendRequest;

/**
 * @author zmm
 * @date 2021/11/16 9:50
 */
public interface INodeService {

    /**
     * 开启选举
     */
    void startElection();

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
     */
    void levelDown();
}
