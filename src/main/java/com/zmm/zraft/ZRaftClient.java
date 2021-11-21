package com.zmm.zraft;

import com.zmm.zraft.gRpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * @author zmm
 * @date 2021/11/17 12:44
 */
public class ZRaftClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder
                                    .forAddress("127.0.0.1", 8080)
                                    .usePlaintext()
                                    .enableRetry()
                                    .maxRetryAttempts(3)
                                    .build();

        RPCServiceGrpc.RPCServiceBlockingStub blockingStub = RPCServiceGrpc.newBlockingStub(channel);
        ClientResponse response = blockingStub.sendCommand(Command.newBuilder().addCommand("b").build());
        System.out.println(response.toString());
    }
}
