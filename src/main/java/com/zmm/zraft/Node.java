package com.zmm.zraft;

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

    public long getId() {
        return id;
    }

    public void addTerm() {
        term.addCurrentTerm();
    }

    public void setTermNum(long termNum) {
        term.setCurrentTerm(termNum);
    }

    public long getCurrentTerm() {
        return term.getCurrentTerm();
    }

    public long getVotedFor() {
        return term.getVotedFor();
    }

    public void setVotedFor(long votedFor) {
        term.setVotedFor(votedFor);
    }

    public long getLeaderId() {
        return term.getLeaderId();
    }

    public void setLeaderId(long leaderId) {
        term.setLeaderId(leaderId);
    }

    public long getCommitIndex() {
        return term.getCommitIndex();
    }

    public long getLogIndex() {
        return term.getLogIndex();
    }

    public int getLastLogTerm() {
        return term.getLastLogTerm();
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public void setNodeState(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    public enum NodeState {
        FOLLOWER, CANDIDATE, LEADER
    }
}
