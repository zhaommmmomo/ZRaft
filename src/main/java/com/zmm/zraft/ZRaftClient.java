package com.zmm.zraft;

import com.zmm.zraft.gRpc.AppendRequest;
import com.zmm.zraft.gRpc.RPCServiceGrpc;
import com.zmm.zraft.gRpc.ZRaftResponse;
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

        for (int i = 0; i < 5; i++) {
            ZRaftResponse response = blockingStub.appendEntries(AppendRequest.newBuilder().build());
        }
    }
}
