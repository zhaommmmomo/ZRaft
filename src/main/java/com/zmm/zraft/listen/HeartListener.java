package com.zmm.zraft.listen;

import com.zmm.zraft.Node;
import com.zmm.zraft.NodeManager;

/**
 * Leader发送心跳包
 * @author zmm
 * @date 2021/11/16 18:25
 */
public class HeartListener implements Runnable{

    @Override
    public void run() {
        // 如果当前节点是Leader节点
        if (NodeManager.node.getNodeState() == Node.NodeState.LEADER) {
            // 发送心跳包
        }
    }
}
