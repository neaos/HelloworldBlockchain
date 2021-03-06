package com.xingkaichun.helloworldblockchain.core.impl;

import com.xingkaichun.helloworldblockchain.core.BlockChainDataBase;
import com.xingkaichun.helloworldblockchain.core.Consensus;
import com.xingkaichun.helloworldblockchain.model.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作量证明
 */
public class ProofOfWorkConsensus extends Consensus {

    private Logger logger = LoggerFactory.getLogger(ProofOfWorkConsensus.class);

    @Override
    public boolean isReachConsensus(BlockChainDataBase blockChainDataBase, Block block) {
        //区块中写入的区块Hash
        String hash = block.getHash();
        //挖矿难度
        String difficulty = difficulty(blockChainDataBase,block);
        return isHashRight(difficulty,hash);
    }

    /**
     * 共识出的挖矿的难度。挖矿的难度决定了nonce获取难度。根本上讲，首先形成挖矿的难度共识，然后倒着推算出nonce。
     *
     * @param blockChainDataBase 区块链
     * @param block              目标区块
     */
    private String difficulty(BlockChainDataBase blockChainDataBase, Block block){
        return "000000";
    }

    /**
     * Hash满足挖矿难度的要求吗？
     * @param targetDificulty 目标挖矿难度
     * @param hash 需要校验的Hash
     */
    private boolean isHashRight(String targetDificulty, String hash){
        return hash.startsWith(targetDificulty);
    }
}
