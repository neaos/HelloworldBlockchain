package com.xingkaichun.helloworldblockchain.core;

import com.xingkaichun.helloworldblockchain.model.Block;

/**
 * 挖矿共识
 * 区块链是一个分布式的数据库。任何节点都可以产生下一个区块，如果同时有多个节点都产生了下一个区块，
 * 以哪个节点产生的区块为准？
 * 理想状态下，我们希望整个区块链网络下一个区块只产生一个，这样就不存在以哪个为准的问题了。
 * 因此，我们应当控制区块的产生，
 * 因此节点之间应当达成一个共识：下一个区块应当是什么样的？这样，下一个区块不是随意生成的了。
 * 当然，即使有了区块产生的共识，也有可能多个节点都产生了下一个区块(有了共识，产生的下一个区块少了很多很多)。
 * 这个问题，就让他们继续竞争下去，看谁能产生下下个区块。
 */
public abstract class Consensus {

    /**
     * 这个区块写入的nonce达成共识了吗？
     *
     * @param blockChainDataBase 区块链
     * @param block              需要被验证nonce是否已经达成了共识的区块
     */
    public abstract boolean isReachConsensus(BlockChainDataBase blockChainDataBase, Block block);
}

