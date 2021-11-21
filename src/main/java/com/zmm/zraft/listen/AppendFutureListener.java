package com.zmm.zraft.listen;

import com.google.common.util.concurrent.ListenableFuture;
import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.ClientResponse;
import com.zmm.zraft.gRpc.ZRaftResponse;
import com.zmm.zraft.service.IZRaftService;
import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
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
    private static List<ListenableFuture<ZRaftResponse>> futureList = new ArrayList<>();

    /**
     * 用于记录集群中append成功的节点数
     */
    private volatile static int count = 1;

    /**
     * 重试次数。当超过6次就结束
     */
    private volatile static int retryCount = 1;

    /**
     * 需要添加的条目个数
     */
    private static int entriesCount = 0;

    /**
     * 是否成功返回
     */
    private volatile static boolean flag = false;

    /**
     * 返回器
     */
    public static StreamObserver<ClientResponse> responseObserver;

    public static ClientResponse.Builder res =
            ClientResponse.newBuilder().setLeaderId(NodeManager.node.getLeaderId());

    /**
     * method
     */
    private final IZRaftService zRaftService = new ZRaftService();

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
                        if (zRaftResponse.getSuccess()) {
                            count++;
                            if (count > NodeManager.allNodeCounts / 2) {
                                // 如果集群中大多数的节点都append成功
                                // 将结果返回给用户

                                NodeManager.printLog("大多数的节点响应true......");
                                count = 1;
                                flag = true;
                            }
                            int nextIndex = NodeManager.nextIndex.get(i);
                            // 修改该节点的下一个索引值
                            NodeManager.nextIndex.set(i, nextIndex + entriesCount);
                        } else {
                            NodeManager.nextIndex.set(i, NodeManager.nextIndex.get(i) - 1);
                        }
                        futureList.set(i, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // TODO: 2021/11/20 重试逻辑还未完成
    /**
     * 重试
     */
    public void retry() {

    }

    /**
     * 设置本次Append的条目数
     * @param count         条目数
     */
    public static void setEntriesCount(int count) {
        entriesCount = count;
        flag = false;
    }

    /**
     * 添加Future监听
     * @param future            future
     */
    public static synchronized void addFuture(ListenableFuture<ZRaftResponse> future) {
        futureList.add(future);
    }

    public static synchronized void clear() {
        flag = false;
        count = 1;
        futureList.clear();
        futureList = new CopyOnWriteArrayList<>();
    }

    /**
     * 开启失败监听
     */
    public static void response() {
        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
                res.setSuccess(flag);
                responseObserver.onNext(res.build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
