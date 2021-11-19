package com.zmm.zraft;

import com.zmm.zraft.gRpc.RPCServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * @author zmm
 * @date 2021/11/17 12:44
 */
public class ZRaftClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder
                                    .forAddress("127.0.0.1", 8080)
                                    .usePlaintext().build();

        RPCServiceGrpc.RPCServiceBlockingStub blockingStub = RPCServiceGrpc.newBlockingStub(channel);
        System.out.println(blockingStub.requestVote(null));
    }
}
