package com.zmm.zraft;

import com.zmm.zraft.gRpc.Entry;

import java.util.List;
import java.util.Random;

/**
 * 节点信息
 * @author zmm
 * @date 2021/11/16 9:41
 */
public class Node {

    /**
     * 节点id号
     */
    private long id = new Random().nextInt(10000);

    /**
     * 记录当前节点状态
     */
    private NodeState nodeState = NodeState.FOLLOWER;

    /**
     * 任期信息
     */
    private final Term term = new Term();

    /**
     * 判断条目是否存在
     * @param preLogTerm        最后一个条目的任期
     * @param preLogIndex       最后一个条目的索引
     * @return                  true / false
     */
    public boolean entryIsExist(long preLogTerm, long preLogIndex) {
        return term.entryIsExist(preLogTerm, preLogIndex);
    }

    /**
     * 添加日志条目
     * @param entries           指定条目
     * @return                  true / false
     */
    public boolean addLogEntries(List<Entry> entries) {
        return term.addLogEntries(entries);
    }

    /**
     * 添加日志条目
     * @param preLogIndex       最后一个条目的索引
     * @param entries           指定条目
     * @return                  true / false
     */
    public boolean addLogEntries(long preLogIndex, List<Entry> entries) {
        return term.addLogEntries(preLogIndex, entries);
    }

    /**
     * 获取指定条目之前的任期
     * @param nextIndex     指定条目索引
     * @return              任期
     */
    public long getPreTermByIndex(long nextIndex) {
        return term.getPreTermByIndex(nextIndex);
    }

    /**
     * 获取指定下标后的条目
     * @param nextIndex     指定下标
     * @return              条目信息
     */
    public synchronized List<Entry> getEntriesFromIndex(long nextIndex) {
        return term.getEntriesFromIndex(nextIndex);
    }

    /**
     * 提交日志
     * 将提交索引设置为min(leaderCommit, logIndex)
     * @param commitIndex   需要提交的索引末位置
     * @return              true / false
     */
    public synchronized boolean commitLog(long commitIndex) {
        return term.commitLog(commitIndex);
    }

    public long getId() {
        return id;
    }

    /**
     * 任期增加
     */
    public void addTerm() {
        term.addCurrentTerm();
    }

    public synchronized void setTermNum(long termNum) {
        term.setCurrentTerm(termNum);
    }

    public long getCurrentTerm() {
        return term.getCurrentTerm();
    }

    public long getVotedFor() {
        return term.getVotedFor();
    }

    public synchronized void setVotedFor(long votedFor) {
        term.setVotedFor(votedFor);
    }

    public long getLeaderId() {
        return term.getLeaderId();
    }

    public synchronized void setLeaderId(long leaderId) {
        term.setLeaderId(leaderId);
    }

    public long getCommitIndex() {
        return term.getCommitIndex();
    }

    public long getLogIndex() {
        return term.getLogIndex();
    }

    public long getLastLogTerm() {
        return term.getLastLogTerm();
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public synchronized void setNodeState(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + id +
                ", nodeState=" + nodeState +
                ", term=" + term.toString() +
                '}';
    }

    /**
     * 节点状态
     */
    public enum NodeState {
        FOLLOWER, CANDIDATE, LEADER
    }
}
