package com.zmm.zraft;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任期
 * @author zmm
 * @date 2021/11/17 13:17
 */
@Data
public class Term {

    /**
     * 当前任期序号（初始为0，单调递增）
     */
    private long currentTerm = 0;

    /**
     * 当前任期内投票给了谁，0表示没有投票
     */
    private long votedFor = 0;

    /**
     * 当前任期的leaderId。
     * 0：还没有Leader
     * -1：自己就是Leader
     */
    private long leaderId = 0;

    /**
     * 当前任期内收到的命令
     */
    private final List<List<String>> log = new ArrayList<>();

    /**
     * 最后一个已提交命令的索引
     */
    private long commitIndex = 0;

    /**
     * 最后一个命令的索引
     */
    private long logIndex = 0;

    /**
     * 任期增加
     */
    public void addCurrentTerm() {
        this.currentTerm++;
    }

    public int getLastLogTerm() {
        return log.size();
    }
}
