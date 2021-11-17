package com.zmm.zraft;

import com.zmm.zraft.listen.ElectionListener;
import com.zmm.zraft.listen.HeartListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 负责管理节点、定时器等信息
 * @author zmm
 * @date 2021/11/16 9:53
 */
public class NodeManager {

    /**
     * 当前节点信息
     */
    public static final Node node;

    /**
     * 等待定时器
     */
    public static final ElectionListener electionListener;

    /**
     * 心跳包定时器
     */
    private static final ScheduledExecutorService executorService =
                            Executors.newScheduledThreadPool(1);

    static {
        // 初始化节点信息
        node = new Node();
        // 启动定时器
        electionListener = new ElectionListener();
        new Thread(electionListener).start();
        HeartListener heartListener = new HeartListener();
        executorService.scheduleAtFixedRate(heartListener,
                                  60,
                                     60,
                                            TimeUnit.MILLISECONDS);
    }
}
