package com.zmm.zraft.listen;

import com.google.common.util.concurrent.ListenableFuture;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.ZRaftResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zmm
 * @date 2021/11/17 18:14
 */
public class FutureListener implements Runnable{

    /**
     * 最近一次选举获得的票数
     */
    private volatile static int voteCount = 0;

    /**
     * 记录Future
     */
    private static final List<ListenableFuture<ZRaftResponse>> queue = new ArrayList<>();

    @Override
    public void run() {
        synchronized (queue) {
            int l = queue.size();
            for (int i = 0; i < l; i++) {
                ListenableFuture<ZRaftResponse> future = queue.get(i);
                if (future.isDone()){
                    try {
                        ZRaftResponse zRaftResponse = future.get();
                        if (zRaftResponse.getSuccess()) {
                            voteCount++;
                            if (voteCount > (NodeManager.otherNodes.size() + 1) / 2) {
                                // 如果当前的票数超过了一半，触发Leader逻辑
                                // 变为Leader，发送心跳包，设置不会出现等待超时
                                // TODO: 2021/11/17 触发toBeLeader逻辑

                                // 清空FutureTask数据，确保里面没有因宕机每响应的Future
                                voteCount = 0;
                                queue.clear();
                            }

                        } else {
                            //// 获取投票者的任期
                            //long term = zRaftResponse.getTerm();
                            //if (term > NodeManager.node.getCurrentTerm()) {
                            //    // 如果投票者的任期大于当前任期
                            //    // 不用管这个情况，因为任期大的
                            //    // 一定会成为Leader。
                            //}
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    queue.remove(future);
                    if (l == 1) {
                        // 代表当前元素是最后一个
                        // TODO: 2021/11/17 貌似这里不用判断是否是最后一个
                    }
                    break;
                }
            }
        }
    }

    public static void resetVoteCount() {
        voteCount = 1;
    }

    public static void addFuture(ListenableFuture<ZRaftResponse> future) {
        queue.add(future);
    }

    public static void clear() {
        queue.clear();
    }
}
