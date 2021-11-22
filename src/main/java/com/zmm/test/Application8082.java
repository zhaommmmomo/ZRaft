package com.zmm.test;

import com.zmm.zraft.NodeManager;
import com.zmm.zraft.service.impl.ZRaftRPCService;
import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.ServerBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author zmm
 * @date 2021/11/16 10:40
 */
public class Application8082 {
    public static void main(String[] args) {
        try {
            while (true) {
                if (System.currentTimeMillis() > Test.time) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }

            List<Integer> nodes = new ArrayList<>();
            nodes.add(8080);
            nodes.add(8081);

            //启动服务
            io.grpc.Server server = ServerBuilder.forPort(8082).addService(new ZRaftRPCService()).build();
            server.start();
            NodeManager.init(nodes);;
            server.awaitTermination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
