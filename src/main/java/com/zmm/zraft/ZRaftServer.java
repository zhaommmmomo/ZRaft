package com.zmm.zraft;

import com.zmm.zraft.service.impl.ZRaftRPCService;
import io.grpc.ServerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zmm
 * @date 2021/11/17 12:36
 */
public class ZRaftServer {

    public static void main(String[] args) {
        try {
            List<Integer> nodes = new ArrayList<>();
            nodes.add(8081);
            nodes.add(8082);
            nodes.add(8083);
             //启动服务
            io.grpc.Server server = ServerBuilder.forPort(8080).addService(new ZRaftRPCService()).build();
            server.start();
            NodeManager.otherNodes = nodes;
            server.awaitTermination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
