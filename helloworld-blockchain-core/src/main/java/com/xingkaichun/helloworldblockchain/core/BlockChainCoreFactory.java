package com.xingkaichun.helloworldblockchain.core;

import com.xingkaichun.helloworldblockchain.core.impl.*;
import com.xingkaichun.helloworldblockchain.model.key.StringAddress;

import java.io.File;
import java.util.ArrayList;

public class BlockChainCoreFactory {


    public BlockChainCore createBlockChainCore(String blockchainDataPath,String minerAddress) throws Exception {

        Incentive incentive = new IncentiveDefaultImpl();
        Consensus consensus = new ProofOfWorkConsensus();
        BlockChainDataBase blockChainDataBase = new BlockChainDataBaseDefaultImpl(blockchainDataPath,incentive,consensus);

        TransactionDataBase transactionDataBase = new TransactionDataBaseDefaultImpl();

        MinerTransactionDtoDataBase minerTransactionDtoDataBase = new MinerTransactionDtoDtoDataBaseDefaultImpl(blockchainDataPath,transactionDataBase);
        StringAddress minerStringAddress = new StringAddress(minerAddress);
        Miner miner = new MinerDefaultImpl(blockChainDataBase, minerTransactionDtoDataBase,minerStringAddress);

        SynchronizerDataBase synchronizerDataBase = new SynchronizerDataBaseDefaultImpl(blockchainDataPath,transactionDataBase);
        BlockChainDataBase temporaryBlockChainDataBase = new BlockChainDataBaseDefaultImpl(new File(blockchainDataPath,"TemporaryBlockChainDataBase").getAbsolutePath(),incentive,consensus);
        Synchronizer synchronizer = new SynchronizerDefaultImpl(blockChainDataBase,temporaryBlockChainDataBase, synchronizerDataBase);

        BlockChainCore blockChainCore = new BlockChainCoreImpl(new ArrayList<>());
        blockChainCore.setMiner(miner);
        blockChainCore.setSynchronizer(synchronizer);
        blockChainCore.setBlockChainDataBase(blockChainDataBase);
        return blockChainCore;
    }
}
