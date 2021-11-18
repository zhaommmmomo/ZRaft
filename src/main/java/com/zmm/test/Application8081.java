package com.zmm.test;

import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.ServerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zmm
 * @date 2021/11/16 10:40
 */
public class Application8081 {
    public static void main(String[] args) {
        try {
            while (true) {
                if (System.currentTimeMillis() % 10000 / 100 == 10) {
                    break;
                }
            }

            List<Integer> nodes = new ArrayList<>();
            nodes.add(8083);
            nodes.add(8082);
            nodes.add(8080);
            //启动服务
            io.grpc.Server server = ServerBuilder.forPort(8081).addService(new ZRaftService()).build();
            server.start();
            NodeManager.otherNodes = nodes;
            server.awaitTermination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
