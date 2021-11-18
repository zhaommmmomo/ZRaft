package com.zmm.zraft;

import com.zmm.zraft.listen.ElectionListener;
import com.zmm.zraft.listen.HeartListener;
import io.grpc.ManagedChannel;

import java.util.ArrayList;
import java.util.List;

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
     * 集群中所有节点的数量
     */
    public static int allNodeCounts = 1;

    /**
     * 其他节点地址，因为我在同一个机器上操作，
     * 就只记录了Port
     */
    public static List<Integer> otherNodes;

    /**
     * 与其他节点的channel
     */
    public final static List<ManagedChannel> channels = new ArrayList<>();

    /**
     * 等待定时器，Leader不会开启
     */
    public static final ElectionListener electionListener;

    /**
     * 心跳定时器，Leader开启
     */
    public static HeartListener heartListener;

    static {
        System.out.println("==========初始化节点=============");
        // 初始化节点信息
        node = new Node();
        // 启动等待定时器
        electionListener = new ElectionListener();
        new Thread(electionListener).start();

        printNodeLog();
    }

    //public static void init(List<Integer> otherNodes) {
    //    int l = otherNodes.size();
    //    allNodeCounts += l;
    //    for (int i = 0; i < l; i++) {
    //        ManagedChannel channel = ManagedChannelBuilder
    //                .forAddress("127.0.0.1",
    //                        NodeManager.otherNodes.get(i))
    //                .usePlaintext().build();
    //        channels.add(channel);
    //    }
    //}

    public static void printLog(String msg) {
        System.out.println(msg);
    }

    public static void printNodeLog() {
        System.out.println("=======================================");
        System.out.println("==============NodeInfo=================");
        System.out.println("nodeId: " + node.getId());
        System.out.println("term: " + node.getCurrentTerm());
        System.out.println("nodeState: " + node.getNodeState());
        System.out.println("votedFor: " + node.getVotedFor());
        System.out.println("leaderId: " + node.getLeaderId());
        System.out.println("=============NodeManager===============");
        System.out.println("allNodeCounts: " + allNodeCounts);
        System.out.println("=======================================");
    }
}
