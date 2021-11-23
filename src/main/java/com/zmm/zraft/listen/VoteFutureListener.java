package com.zmm.zraft.listen;

import com.google.common.util.concurrent.ListenableFuture;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.service.IZRaftService;
import com.zmm.zraft.service.impl.ZRaftService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zmm
 * @date 2021/11/17 18:14
 */
public class VoteFutureListener implements Runnable{

    /**
     * 最近一次选举获得的票数
     */
    private volatile static int voteCount = 0;

    /**
     * 记录Future
     */
    private static final List<ListenableFuture<ZRaftResponse>> queue = new ArrayList<>();

    /**
     * method
     */
    private final IZRaftService zRaftService = new ZRaftService();

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
                            if (voteCount > NodeManager.allNodeCounts / 2) {

                                NodeManager.printLog("voteCount: " + voteCount);
                                NodeManager.printLog("allNodeCounts: " + NodeManager.allNodeCounts);

                                // 清空FutureTask数据，确保里面没有因宕机每响应的Future
                                voteCount = 0;
                                queue.clear();

                                // 如果当前的票数超过了一半，触发Leader逻辑
                                // 变为Leader，发送心跳包，设置不会出现等待超时
                                zRaftService.toBeLeader();
                                NodeManager.printNodeInfo();
                                break;
                            }

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    queue.remove(future);
                    voteCount = 0;
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
}
