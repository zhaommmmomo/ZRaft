package com.zmm.zraft.listen;

import com.zmm.zraft.service.IZRaftService;
import com.zmm.zraft.service.impl.ZRaftService;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 等待超时
 * @author zmm
 * @date 2021/11/16 18:21
 */
public class ElectionListener implements Runnable{

    /**
     * 是否停止等待超时器
     */
    private volatile static boolean stop = true;

    /**
     * 随机函数
     */
    private static final Random random = new Random();

    /**
     * 基础超时时间
     */
    private final static int TIME = 150;

    /**
     * 上一个心跳包接收到的时间，只有当接收到心跳包时才会被修改
     */
    private volatile static long preHeartTime;

    /**
     * 当前等待超时时间
     */
    private static int currentTimeOut = 500;

    /**
     * 等待超时线程
     */
    private final Thread electionThread = new Thread(this);

    /**
     * method
     */
    private final IZRaftService zRaftService = new ZRaftService();

    public ElectionListener () {
        // 初始化上一个心跳包接收到的时间和等待超时时间
        updatePreHeartTime(System.currentTimeMillis());
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // 节点刚启动的时候不会进入该逻辑里面
            // 会有一个等待超时时间
            // 如果当前时间 - 上一个心跳包接收到的时间 > 当前超时时间
            long t = System.currentTimeMillis();
            if (t - preHeartTime >= currentTimeOut) {
                // 随机生成超时时间
                createRandomTime();
                // 修改上次心跳的时间，重置等待超时器
                // 如果不修改的话，会一直sendVoteRequest()
                updatePreHeartTime(t);
                // 触发开始选举逻辑
                zRaftService.toBeCandidate();
            }
        }
    }

    /**
     * 随机超时数
     */
    private synchronized void createRandomTime() {
        currentTimeOut = random.nextInt(TIME) + TIME;
    }

    /**
     * 修改上一个心跳包到达的时间
     * @param heartTime          当前心跳包到达的时间
     */
    public synchronized void updatePreHeartTime(long heartTime) {
        // 这里可以不用加锁，只有当前节点收到心跳包时才会修改preHeartTime
        // 而在run方法里面，每1ms就会读取一下preHeartTime
        // 所以不用担心多线程环境下带来的影响
        // 还是加锁了（真香） :-)
        preHeartTime = heartTime;
    }

    /**
     * 开启等待计时器
     */
    public synchronized void start() {

        // 重置等待时间
        currentTimeOut = random.nextInt(TIME) + TIME;
        preHeartTime = System.currentTimeMillis();

        if (stop) {
            stop = false;
            electionThread.start();
        }
    }

    /**
     * 关闭等待计时器
     */
    public synchronized void stop () {
        if (!stop) {
            stop = true;
        }
    }
}
