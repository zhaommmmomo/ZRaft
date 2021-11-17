package com.zmm.zraft;


import com.zmm.zraft.service.impl.ZRaftService;
import io.grpc.ServerBuilder;

/**
 * @author zmm
 * @date 2021/11/17 12:36
 */
public class ZRaftServer {

    private static NodeManager nodeManager;

    public static void main(String[] args) {
        try {
             //启动服务
            io.grpc.Server server = ServerBuilder.forPort(8080).addService(new ZRaftService()).build();
            server.start();
            nodeManager = new NodeManager();
            server.awaitTermination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
