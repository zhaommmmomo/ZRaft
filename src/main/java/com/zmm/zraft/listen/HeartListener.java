package com.zmm.zraft.listen;

import com.zmm.zraft.NodeManager;
import com.zmm.zraft.gRpc.AppendRequest;
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
    private static boolean stop = true;

    /**
     * 心跳超时时间
     */
    private static long timeout = 70;

    /**
     * 心跳线程
     */
    private Thread heartThread;

    /**
     * method
     */
    private ZRaftService zRaftService;


    @Override
    public void run() {
        while (!stop) {
            // 发送心跳包
            zRaftService.sendAppendEntries(createHeartPacket());

            // sleep
            try {
                TimeUnit.MILLISECONDS.sleep(timeout);
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
                .setEntries(0, "")
                .setLeaderCommit(NodeManager.node.getCommitIndex())
                .build();
    }

    public void startHeart(long timeout) {
        stop = false;
        HeartListener.timeout = timeout <= 0 ? HeartListener.timeout : timeout;
        if (heartThread == null) {
            heartThread = new Thread(this);
        }
        heartThread.start();
    }

    public void stopHeart() {
        stop = true;
    }
}
