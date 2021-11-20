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

/**
 * @author zmm
 * @date 2021/11/20 17:09
 */
public class AppendFutureListener implements Runnable{

    /**
     * 用于记录是哪一个节点返回的future
     */
    private static final List<ListenableFuture<ZRaftResponse>> futureList = new ArrayList<>();

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
     * 返回器
     */
    public static StreamObserver<ClientResponse> responseObserver;

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
                                ClientResponse response = ClientResponse.newBuilder()
                                        .setSuccess(true)
                                        .setLeaderId(NodeManager.node.getLeaderId())
                                        .build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                            }
                            int nextIndex = NodeManager.nextIndex.get(i);
                            // 修改该节点的下一个索引值
                            NodeManager.nextIndex.set(i, nextIndex + entriesCount);
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
    }

    /**
     * 添加Future监听
     * @param future            future
     */
    public static synchronized void addFuture(ListenableFuture<ZRaftResponse> future) {
        futureList.add(future);
    }
}
