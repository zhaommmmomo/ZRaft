package com.zmm.zraft;

import com.zmm.zraft.gRpc.Entry;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
     */
    private long leaderId = 0;

    /**
     * 当前任期内收到的命令
     */
    private List<Entry> log = new CopyOnWriteArrayList<>();

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

    /**
     * 获取当前节点的日志任期条目
     * @return                  当前节点的日志任期条目
     */
    public long getLastLogTerm() {
        if (logIndex == 0) {
            return 0;
        }
        return log.get((int) (logIndex - 1)).getTerm();
    }

    /**
     * 判断条目是否存在
     * @param preLogTerm        最后一个条目的任期
     * @param preLogIndex       最后一个条目的索引
     * @return                  true / false
     */
    public boolean entryIsExist(long preLogTerm, long preLogIndex) {

        if (logIndex < preLogIndex) {
            // 如果当前节点条目索引小于Leader的条目索引
            return false;
        }

        if (preLogIndex == 0) {
            // 如果Leader的索引为0，直接返回true
            return true;
        }

        // 是否能在条目中找到
        return log.get((int) preLogIndex - 1).getTerm() == preLogTerm;
    }

    /**
     * 添加日志条目
     * @param entries           指定条目
     * @return                  true / false
     */
    public synchronized boolean addLogEntries(List<Entry> entries) {
        // 追加条目
        boolean flag = log.addAll(entries);
        logIndex = log.size();
        return flag;
    }

    /**
     * 添加日志条目
     * @param preLogIndex       最后一个条目的索引
     * @param entries           指定条目
     * @return                  true / false
     */
    public synchronized boolean addLogEntries(long preLogIndex, List<Entry> entries) {

        // 如果当前节点的日志索引大于Leader的（冲突）
        // 将本地冲突的索引删除
        if (logIndex > preLogIndex) {
            log = log.subList(0, (int) preLogIndex);
        }

        // 追加条目
        boolean flag = log.addAll(entries);
        logIndex = log.size();
        return flag;
    }

    /**
     * 提交日志
     * 将提交索引设置为min(leaderCommit, logIndex)
     * @param commitIndex   需要提交的索引末位置
     * @return              true / false
     */
    public synchronized boolean commitLog(long commitIndex) {
        this.commitIndex = Math.min(commitIndex, logIndex);
        // 执行存储逻辑
        return true;
    }

    /**
     * 获取指定条目之前的任期
     * @param nextIndex     指定条目索引
     * @return              任期
     */
    public long getPreTermByIndex(long nextIndex) {
        if (nextIndex == 0) {
            return 0;
        }
        if (nextIndex > logIndex) {
            //throw new RuntimeException("索引越界......");
            NodeManager.printLog("logIndex: " + logIndex + " nextIndex: "+ nextIndex + " 索引越界......");
        }
        return log.get((int) (nextIndex - 1)).getTerm();
    }

    /**
     * 获取指定下标后的条目
     * @param nextIndex     指定下标
     * @return              条目信息
     */
    public synchronized List<Entry> getEntriesFromIndex(long nextIndex) {
        if (nextIndex > logIndex) {
            return new CopyOnWriteArrayList<>();
        }
        if (nextIndex <= 0) {
            return log;
        }
        return log.subList((int) nextIndex, (int) logIndex);
    }

    @Override
    public synchronized String  toString() {
        return "Term{" +
                "currentTerm=" + currentTerm +
                ", votedFor=" + votedFor +
                ", leaderId=" + leaderId +
                ", log=" + log.toString() +
                ", commitIndex=" + commitIndex +
                ", logIndex=" + logIndex +
                '}';
    }
}
