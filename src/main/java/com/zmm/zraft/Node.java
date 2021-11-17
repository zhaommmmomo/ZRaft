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
    private long id = new Random().nextLong();

    /**
     * 记录当前节点状态
     */
    private NodeState nodeState = NodeState.FOLLOWER;

    /**
     * 任期信息
     */
    private Term term;


    public long getCurrentTerm() {
        return term.getCurrentTerm();
    }

    public long getVotedFor() {
        return term.getVotedFor();
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

    public enum NodeState {
        FOLLOWER, CANDIDATE, LEADER
    }
}
