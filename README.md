## 前言

7月份的时候参加了一个阿里天池的性能优化比赛，后面在复赛的时候因为是集群场景，需要考虑各个节点之间数据的一致性，本来想自己实现的，但奈何时间太短（ps: 太菜了😭），最终还是找了市面上成熟的中间件来实现（Ignite）。这不，还是手痒，自己实现一个基于Raft的一致性服务。

Github：[zraft](https://github.com/zhaommmmomo/zraft)

个人博客：[zhaommmmomo](http://zhaommmmomo.cn)

<!--more-->

<br>

<br>

## Raft

### 是什么？

**Raft是一个为了管理复制日志的一致性算法**。它提供和Paxos算法相同的功能和性能，但是它的算法结构与Paxos不同并且更加易于理解

Raft 通过选举一个Leader，然后给予他全部的管理复制日志的责任来实现一致性。领导人从客户端接收日志条目（log entries），把日志条目复制到其他服务器上，并告诉其他的服务器什么时候可以安全地将日志条目应用到他们的状态机中。**数据的流向只能是Leader -> otherNode**。

<br>

### 状态机

![](https://img-blog.csdnimg.cn/83bcda9283f1406da02a959fd41513b8.png#pic_center)

复制状态机通常都是基于复制日志实现的，如图 1。每一个服务器存储一个包含一系列指令的日志，并且按照日志的顺序进行执行。每一个日志都按照相同的顺序包含相同的指令，所以每一个服务器都执行相同的指令序列。因为每个状态机都是确定的，每一次执行操作都产生相同的状态和同样的序列。

一致性算法的任务是保证复制日志的一致性。服务器上的一致性模块接收客户端发送的指令然后添加到自己的日志中。它和其他服务器上的一致性模块进行通信来保证每一个服务器上的日志最终都以相同的顺序包含相同的请求，即使有些服务器发生故障。一旦指令被正确的复制，每一个服务器的状态机按照日志顺序处理他们，然后输出结果被返回给客户端。因此，服务器集群看起来形成了一个高可靠的状态机。

实际系统中使用的一致性算法通常含有以下特性：

* 安全性保证（绝对不会返回一个错误的结果）：在非拜占庭错误情况下，包括网络延迟、分区、丢包、重复和乱序等错误都可以保证正确。
* 可用性：集群中只要有大多数的机器可运行并且能够相互通信、和客户端通信，就可以保证可用。因此，一个典型的包含 5 个节点的集群可以容忍两个节点的失败。服务器被停止就认为是失败。它们稍后可能会从可靠存储的状态中恢复并重新加入集群。
* 不依赖时序来保证一致性：物理时钟错误或者极端的消息延迟只有在最坏情况下才会导致可用性问题。
* 通常情况下，一条指令可以尽可能快的在集群中大多数节点响应一轮远程过程调用时完成。小部分比较慢的节点不会影响系统整体的性能。

<br>

### 概念

#### 节点状态

![](https://img-blog.csdnimg.cn/2cf5f095532b468eaac9745277b3a2b1.png#pic_center)

- **Leader**：负责处理所有Client请求，并将entries通过AppendEntries()RPC方法添加到其他节点去。
- **Candidate**：可以变为Leader的节点。当某一段时间内没有收到心跳或者收到的大多数票数时，就会变为Leader，给其他节点发送心跳。否则变为Follower
- **Follower**：只响应来自其他服务器的请求。集群刚启动时，所有节点状态都是Follower，当某一段时间内没有收到其他节点的信息，就会变为Candidate并向其他节点请求投票。

<br>

#### 任期（term）

![](https://img-blog.csdnimg.cn/26d66796538449c79def94b178f38008.png#pic_center)

Raft将**任期（term）**作为逻辑时间。任期自增的整数表示（初始为0）。每一段任期从一次**选举**开始，如果一个候选人赢得选举，然后他就在接下来的任期内充当领导人的职责。在某些情况下，一次选举过程会造成选票的瓜分。在这种情况下，**这一任期会以没有领导人结束**；一个新的任期（和一次新的选举）会很快重新开始。Raft 保证了在一个给定的任期内，最多只有一个领导人。

<br>

#### 选举方法（RequestVote()）

- 如果term < currentTerm返回 false
- 如果 votedFor 为空或者为 candidateId，并且候选人的日志至少和自己一样新，那么就投票给他

```java
public VoteResponse requestVote(VoteRequest voteRequest) {
    // 如果term < currentTerm返回 false
    // 如果 votedFor 为空或者为 candidateId，并且候选人的日志至少和自己一样新，那么就投票给他
}
Class VoteRequest {
    /** 候选人的任期号 */
    long term;
    /** 候选人Id */
    long candidateId;
    /** 候选人的最后日志条目的索引值 */
    long lastLogIndex;
    /** 候选人最后日志条目的任期号 */
	long lastLogTerm
}
class VoteResponse {
    /** 当前节点的任期号 */
    long term;
    /** 是否投票 */
    boolean voteGranted;
}
```

集群刚启动的时候，所有节点都是Follower状态。如果Follower在选举超时内每收到心跳或者投票请求，它就会进行选举投票，先增加自己的任期号并转换为Candidate，然后向其他节点发送RPC投票请求。

1. 获得了大多数的选票。修改状态为Leader，修改维护的`nextIndex[]`数组为当前日志条目的索引，关闭等待超时计时器，开启心跳计时器并发送心跳包。
2. 其他节点成为Leader。如果Leader的任期号不小于当前任期号，修改状态为Follower。
3. 出现同票情况。**随机生成超时时间**后重新开始新一轮的选举。

<br>

#### 追加条目（AppendEntries()）

只能由Leader -> 其他节点，不能到Leader，是单向的。

Client发送RPC请求，Leader首先会将日志追加到本地，追加失败则返回false。然后通过AppendEntries()方法同步到其他节点上去，当Leader收到大多数节点响应true时，会将该日志条目Commit，然后将结果返回给Client，然后通知其他节点Commit。

![在这里插入图片描述](https://img-blog.csdnimg.cn/1c3ecc44b48e4c98ba95afb371da216d.png#pic_center)

当追加的条目为空时，代表这是个心跳包

- 如果当前任期大于请求任期，返回false
- 如果当前日志条目没有能够与preLogIndex和preLogTerm匹配的，返回false
- 重置等待计时器等待时间
- 如果发生条目冲突（索引相同，任期不同），删除冲突索引以后的所有日志
- 追加日志条目
- 如果Leader的commitIndex大于本地的，将本地的设置为min(commitIndex. logIndex) 

```java
public AppendResponse appendEntries(AppendRequest appendRequest) {
    // 如果当前任期大于请求任期，返回false
    // 如果当前日志条目没有能够与preLogIndex和preLogTerm匹配的，返回false
    // 重置等待计时器等待时间
    // 如果发生条目冲突（索引相同，任期不同），删除冲突索引以后的所有日志
	// 追加日志条目
    // 如果Leader的commitIndex大于本地的，将本地的设置为min(commitIndex. logIndex)
}
class AppendRequest {
    /** Leader的任期号 */
    long term;
    /** LeaderId */
    long leaderId;
    /** 新日志的前一个日志条目的索引 */
    long preLogIndex;
    /** 新日志的前一个日志条目的任期号 */
	long preLogTerm;
    /** 需要添加的条目信息 */
    List<Entry> entries;
    /** Leader的提交索引 */
	long leaderCommit;
}
class Entry {
    long term;
    /** 命令 */
    String command;
}
class AppendResponse {
    /** 当前节点的任期号 */
    long term;
    /** Follower的条目是否与Leader的匹配上了 */
    boolean success;
}
```

Leader对于每个Follower都维护 一个`nextIndex`，记录需要给该Follower发送的下一个日志条目的索引。当某一个节点刚成为Leader时，它会将所有`nextIndex`设置为自己的最后一个日志的`index + 1`。如果一个Follower的日志和Leader不一致，那么在下一次的`AppendEntries()` RPC 时的一致性检查就会失败。在被Follower拒绝之后，Leader就会减小 nextIndex 值并进行重试。最终 nextIndex 会在某个位置使得Leader和Follower的日志达成一致。当这种情况发生，附加日志 RPC 就会成功，这时就会把Follower冲突的日志条目全部删除并且加上Leader的日志。一旦附加日志 RPC 成功，那么Follower的日志就会和Leader保持一致，并且在接下来的任期里一直继续保持。

<br>

<br>

## 详细设计



## 参考资料

[Raft论文](https://github.com/maemual/raft-zh_cn/blob/master/raft-zh_cn.md)

[Raft动态展示](http://thesecretlivesofdata.com/raft)

