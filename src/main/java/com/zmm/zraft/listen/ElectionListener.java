package com.zmm.zraft.listen;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Follower等待
 * @author zmm
 * @date 2021/11/16 18:21
 */
public class ElectionListener implements Runnable{

    /**
     * 等待超时前是否收到Leader的信息
     */
    public volatile static Boolean flag = false;

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
                // 执行选举逻辑，重新设置超时时间
                // 1. 将当前节点设置设置为Candidate并为自己投票
                //Node.votedFor =

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
