package com.zmm.zraft.listen;

import com.google.common.util.concurrent.ListenableFuture;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.ClientResponse;
import com.zmm.zraft.gRpc.ZRaftResponse;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author zmm
 * @date 2021/11/20 17:09
 */
public class AppendFutureListener implements Runnable{

    /**
     * 用于记录是哪一个节点返回的future
     */
    private static List<ListenableFuture<ZRaftResponse>> futureList = new CopyOnWriteArrayList<>();

    /**
     * 用于记录集群中append成功的节点数
     */
    private volatile static int count = 1;

    /**
     * 需要添加的条目个数
     */
    private static final List<Integer> entriesCount = new CopyOnWriteArrayList<>();

    /**
     * 是否成功返回
     */
    private volatile static boolean flag = false;

    /**
     * 返回器
     */
    public static StreamObserver<ClientResponse> responseObserver;

    /**
     * 返回给用户的builder
     */
    public static ClientResponse.Builder res =
            ClientResponse.newBuilder().setLeaderId(NodeManager.node.getLeaderId());

    @Override
    public void run() {
        synchronized (futureList) {
            int l = futureList.size();
            for (int i = 0; i < l; i++) {
                ListenableFuture<ZRaftResponse> future;
                if (futureList.get(i) != null &&
                        (future = futureList.get(i)).isDone()) {
                    try {
                        ZRaftResponse zRaftResponse = future.get();
                        int nextIndex = NodeManager.nextIndex.get(i);
                        if (zRaftResponse.getSuccess()) {
                            count++;
                            if (count > NodeManager.allNodeCounts / 2) {
                                // 如果集群中大多数的节点都append成功
                                // 将结果返回给用户

                                NodeManager.printLog("大多数的节点响应true......");
                                count = 1;
                                flag = true;
                            }
                            // 修改该节点的下一个索引值
                            NodeManager.nextIndex.set(i, nextIndex + entriesCount.get(i));
                        } else {
                            NodeManager.nextIndex.set(i, Math.max(nextIndex - 1, 0));
                        }
                        entriesCount.set(i, 0);
                        futureList.set(i, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 本次添加的条目数
     * @param count         条目数
     */
    public synchronized static void addEntriesCount(int count) {
        entriesCount.add(count);
    }

    /**
     * 获取指定下标的条目数
     * @param index         下标
     * @return              条目数
     */
    public synchronized static int getEntriesCount(int index) {
        return entriesCount.get(index);
    }

    /**
     * 设置指定下标的条目数
     * @param index         指定下标
     * @param count         条目数
     */
    public synchronized static void setEntriesCount(int index, int count) {
        entriesCount.set(index, count);
    }

    /**
     * 添加Future监听
     * @param future            future
     */
    public static synchronized void addFuture(ListenableFuture<ZRaftResponse> future) {
        futureList.add(future);
    }

    /**
     * 清空
     */
    public static synchronized void clear() {
        flag = false;
        count = 1;
        futureList.clear();
        futureList = new CopyOnWriteArrayList<>();
    }

    /**
     * 开启返回监听
     */
    public static void response() {
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
                res.setSuccess(flag);
                res.setLeaderId(NodeManager.node.getLeaderId());
                responseObserver.onNext(res.build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
