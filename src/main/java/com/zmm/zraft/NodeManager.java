package com.zmm.zraft;

import com.zmm.zraft.gRpc.RPCServiceGrpc;
import com.zmm.zraft.listen.ElectionListener;
import com.zmm.zraft.listen.HeartListener;
import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 负责管理节点、定时器等信息
 * @author zmm
 * @date 2021/11/16 9:53
 */
public class NodeManager {

    private static final SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd hh:mm:ss");

    /**
     * 当前节点信息
     */
    public static Node node;

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
     * 记录需要发送给每个节点条目的索引
     */
    public static List<Integer> nextIndex;

    /**
     * 等待定时器，Leader不会开启
     */
    public static ElectionListener electionListener;

    /**
     * 心跳定时器，Leader开启
     */
    public static HeartListener heartListener;

    public static void init(List<Integer> nodes) {
        System.out.println("==========初始化节点=============");
        // 初始化节点信息
        node = new Node();
        otherNodes = nodes;

        ZRaftService.rpcFutureMethod = new ArrayList<>();
        int l = otherNodes.size();
        nextIndex = new ArrayList<>(l);
        allNodeCounts += l;
        for (Integer otherNode : otherNodes) {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("127.0.0.1",
                            otherNode)
                    .usePlaintext()
                    .build();
            ZRaftService.rpcFutureMethod.add(RPCServiceGrpc.newFutureStub(channel));

            nextIndex.add(0);
        }
        // 启动等待定时器
        electionListener = new ElectionListener();
        heartListener = new HeartListener();
        electionListener.start();
        printNodeLog();
    }

    public static void printLog(String msg) {
        System.out.println(ft.format(new Date()) + "  " + msg);
    }

    public static void printNodeLog() {
        System.out.println("=========  " + ft.format(new Date()) + "  =======");
        System.out.println("==============NodeInfo=================");
        System.out.println("nodeId: " + node.getId());
        System.out.println("term: " + node.getCurrentTerm());
        System.out.println("nodeState: " + node.getNodeState());
        System.out.println("votedFor: " + node.getVotedFor());
        System.out.println("leaderId: " + node.getLeaderId());
        System.out.println("allNodeCOunt: " + allNodeCounts);
        System.out.println("=======================================");
    }
}
