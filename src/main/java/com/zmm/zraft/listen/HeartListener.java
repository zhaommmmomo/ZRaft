package com.zmm.zraft.listen;

import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.service.IZRaftService;
import com.zmm.zraft.service.impl.ZRaftService;

import java.util.concurrent.TimeUnit;

/**
 * Leader发送心跳包
 * @author zmm
 * @date 2021/11/16 18:25
 */
public class HeartListener implements Runnable{

    /**
     * 是否停止心跳包的发送
     */
    private volatile static boolean stop = true;

    /**
     * 心跳超时时间
     */
    private static long TIMEOUT = 70;

    /**
     * 心跳线程
     */
    private final Thread heartThread = new Thread(this);

    /**
     * method
     */
    private final IZRaftService zRaftService = new ZRaftService();


    @Override
    public void run() {
        while (!stop) {
            NodeManager.printLog("sent heart...");
            // 发送心跳包
            zRaftService.sendAppendEntries(createHeartPacket());

            // sleep
            try {
                TimeUnit.MILLISECONDS.sleep(TIMEOUT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 构建心跳包
     * @return          心跳包
     */
    private AppendRequest createHeartPacket() {
        return AppendRequest.newBuilder()
                .setTerm(NodeManager.node.getCurrentTerm())
                .setLeaderId(NodeManager.node.getId())
                .setPreLogIndex(NodeManager.node.getLogIndex())
                .setPreLogTerm(NodeManager.node.getLastLogTerm())
                .setLeaderCommit(NodeManager.node.getCommitIndex())
                .build();
    }

    /**
     * 开启心跳计时器
     */
    public synchronized void start() {
        start(TIMEOUT);
    }

    /**
     * 开启心跳计时器并设置心跳时间
     * @param timeout           心跳时间
     */
    public synchronized void start(long timeout) {
        if (stop) {
            stop = false;
            TIMEOUT = timeout <= 0 ? TIMEOUT : timeout;
            heartThread.start();
        }
    }

    /**
     * 停止心跳计时器
     */
    public synchronized void stop() {
        if (!stop) {
            stop = true;
        }
    }
}
