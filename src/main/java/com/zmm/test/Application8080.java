package com.zmm.test;

import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.ServerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zmm
 * @date 2021/11/18 14:45
 */
public class Application8080 {
    public static void main(String[] args) {
        try {
            while (true) {
                if (System.currentTimeMillis() % 10000 / 100 == 10) {
                    break;
                }
            }

            List<Integer> nodes = new ArrayList<>();
            nodes.add(8081);
            nodes.add(8082);
            nodes.add(8083);
            //启动服务
            io.grpc.Server server = ServerBuilder.forPort(8080).addService(new ZRaftService()).build();
            server.start();
            NodeManager.otherNodes = nodes;
            server.awaitTermination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
