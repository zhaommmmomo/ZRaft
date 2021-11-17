package com.zmm.zraft.rpc;

/**
 * @author zmm
 * @date 2021/11/16 9:51
 */
public interface IRpcService {

    /**
     * 节点选举
     * 选举超时：Follower等待成为Leader的时间，随机设置在150ms ~ 300ms
     * @param term              候选人的任期号
     * @param candidateId       候选人ID
     * @param lastLogIndex      候选人最后的日志条目索引值
     * @param lastLogTerm       候选人最后日志条目的任期号
     * @return                  {
     *                              "term": 当前任期号
     *                              "voteGranted": true / false
     *                                              是否被投票
     *                          }
     */
    //ZRaftResponse requestVote(VoteRequest request);

    /**
     * 追加条目，心跳，节点间数据的同步，日志复制
     * 1. Leader接收到数据更改，将更改添加到节点日志中（不提交）
     * 2. 将该条目复制到Follower，等待回复，直到大多数（n / 2 + 1）
     *    节点响应成功。如果没有超过半数的节点响应成功，隔段超时时间后重新发送
     * 3. Leader提交数据，然后将结果返回给并通知Follower进行提交
     */
    /**
     * 追加条目，心跳，节点间数据的同步，日志复制
     * 1. Leader接收到数据更改，将更改添加到节点日志中（不提交）
     * 2. 将该条目复制到Follower，等待回复，直到大多数（n / 2 + 1）
     *    节点响应成功。如果没有超过半数的节点响应成功，隔段超时时间后重新发送
     * 3. Leader提交数据，然后将结果返回给并通知Follower进行提交
     * @param term              Leader的任期
     * @param leaderId          有时候可能是Candidate收到请求，这时候
     *                          就需要将请求重定向到Leader去
     * @param preLogIndex       前一个日志条目的索引
     * @param preLogTerm        前一个日志条目的任期
     * @param entries           需要被保存的日志条目（如果为空，代表是心跳）
     * @param leaderCommit      Leader已提交的最高日志条目的索引
     * @return                  {
     *                              "term": 当前任期
     *                              "success": true / false。如果Candidate
     *                              所含有的条目和prevLogIndex以及preLogTerm
     *                              匹配上，则为true。
     *                          }
     */
    //ZRaftResponse appendEntries(AppendRequest request);

    /**
     * 选举超时后，Follower成为Candidate并开始新的选举任期（term），
     * 为自己投票并向其他节点发送请求投票消息，如果接收节点在这个term内
     * 还没有投票，那么它会投票给Candidate并且重置自身节点的选举超时。
     * 一旦Candidate获得多数票，他就会成为Leader，Leader开始向
     * 其他追随者发送追加条目消息，这些消息按心跳超时指定的时间间隔发送。
     * 追随者然后响应每个附加条目（Append Entries）消息，这个term
     * 持续到Candidate停止接收心跳并成为候选人。
     *
     * 如果产生分裂投票，随机设置超时后重新开始选举
     */
}
