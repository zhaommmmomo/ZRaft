package com.zmm.zraft.listen;

import com.zmm.zraft.service.impl.ZRaftService;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 等待超时
 * @author zmm
 * @date 2021/11/16 18:21
 */
public class ElectionListener implements Runnable{

    private static final Random random = new Random();

    private final static int TIME = 150;

    /**
     * 上一个心跳包接收到的时间，只有当接收到心跳包时才会被修改
     */
    private volatile long preHeartTime;

    /**
     * 当前等待超时时间
     */
    private int currentTimeOut;

    /**
     * method
     */
    private final ZRaftService zRaftService = new ZRaftService();

    public ElectionListener () {
        // 初始化上一个心跳包接收到的时间和等待超时时间
        preHeartTime = System.currentTimeMillis();
        currentTimeOut = random.nextInt(TIME) + TIME;
    }

    @Override
    public void run() {
        while (true) {
            // 节点刚启动的时候不会进入该逻辑里面
            // 会有一个等待超时时间
            // 如果当前时间 - 上一个心跳包接收到的时间 > 当前超时时间
            if (System.currentTimeMillis() - preHeartTime >= currentTimeOut) {
                // 随机生成超时时间
                currentTimeOut = random.nextInt(TIME) + TIME;
                // 触发开始选举逻辑
                zRaftService.startElection();
            }
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 修改上一个心跳包到达的时间
     * @param preHeartTime          当前心跳包到达的时间
     */
    public void updatePreHeartTime(long preHeartTime) {
        // 这里可以不用加锁，只有当前节点收到心跳包时才会修改preHeartTime
        // 而在run方法里面，每1ms就会读取一下preHeartTime
        // 所以不用担心多线程环境下带来的影响
        this.preHeartTime = preHeartTime;
    }
}