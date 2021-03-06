package com.xingkaichun.helloworldblockchain.core.impl;

import com.xingkaichun.helloworldblockchain.core.BlockChainDataBase;
import com.xingkaichun.helloworldblockchain.core.Consensus;
import com.xingkaichun.helloworldblockchain.core.Incentive;
import com.xingkaichun.helloworldblockchain.core.exception.BlockChainCoreException;
import com.xingkaichun.helloworldblockchain.core.utils.BlockUtils;
import com.xingkaichun.helloworldblockchain.core.utils.atomic.*;
import com.xingkaichun.helloworldblockchain.model.Block;
import com.xingkaichun.helloworldblockchain.model.enums.BlockChainActionEnum;
import com.xingkaichun.helloworldblockchain.model.key.StringAddress;
import com.xingkaichun.helloworldblockchain.model.key.StringPublicKey;
import com.xingkaichun.helloworldblockchain.model.transaction.Transaction;
import com.xingkaichun.helloworldblockchain.model.transaction.TransactionInput;
import com.xingkaichun.helloworldblockchain.model.transaction.TransactionOutput;
import com.xingkaichun.helloworldblockchain.model.transaction.TransactionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.impl.WriteBatchImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * 区块链
 *
 * 注意这是一个线程不安全的实现。在并发的情况下，不保证功能的正确性。
 */
public class BlockChainDataBaseDefaultImpl extends BlockChainDataBase {

    private Logger logger = LoggerFactory.getLogger(BlockChainDataBaseDefaultImpl.class);

    //region 变量
    private final static String BlockChain_DataBase_DirectName = "BlockChainDataBase";
    //区块链数据库
    private DB blockChainDB;

    //区块链高度key：它对应的值是区块链的高度
    private final static String BLOCK_CHAIN_HEIGHT_KEY = "B_C_H_K";
    //区块高度标识：存储区块链高度到区块的映射
    private final static String BLOCK_HEIGHT_PREFIX_FLAG = "B_H_P_F_";
    //区块Hash标识：存储区块链Hash到区块的映射
    private final static String BLOCK_HASH_PREFIX_FLAG = "B_HA_P_F_";
    //交易标识：存储交易UUID到交易的映射
    private final static String TRANSACTION_UUID_PREFIX_FLAG = "T_U_P_F_";
    //交易输出标识：存储交易输出UUID到交易输出的映射
    private final static String TRANSACTION_OUTPUT_UUID_PREFIX_FLAG = "T_O_U_P_F_";
    //未花费的交易输出标识：存储未花费交易输出UUID到未花费交易输出的映射
    private final static String UNSPEND_TRANSACTION_OUPUT_UUID_PREFIX_FLAG = "U_T_O_U_P_F_";
    //UUID标识：UUID(交易UUID、交易输出UUID)的前缀，这里希望系统中所有使用到的UUID都是不同的
    private final static String UUID_PREFIX_FLAG = "U_F_";
    //地址标识：存储地址到交易输出的映射
    private final static String ADDRESS_TO_TRANSACTION_OUPUT_LIST_KEY_PREFIX_FLAG = "A_T_T_O_P_F_";
    //地址标识：存储地址到未花费交易输出的映射
    private final static String ADDRESS_TO_UNSPEND_TRANSACTION_OUPUT_LIST_KEY_PREFIX_FLAG = "A_T_U_T_O_P_F_";

    /**
     * 锁:保证对区块链增区块、删区块的操作是同步的。
     * 查询区块操作不需要加锁，原因是，只有对区块链进行区块的增删才会改变区块链的数据。
     */
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    //endregion

    //region 构造函数
    public BlockChainDataBaseDefaultImpl(String blockchainDataPath,Incentive incentive,Consensus consensus) throws Exception {
        this.blockChainDB = LevelDBUtil.createDB(new File(blockchainDataPath,BlockChain_DataBase_DirectName));
        this.incentive = incentive ;
        this.consensus = consensus ;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                blockChainDB.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
    //endregion

    //region 区块增加与删除
    @Override
    public boolean addBlock(Block block) throws Exception {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try{
            boolean isBlockCanApplyToBlockChain = isBlockCanApplyToBlockChain(block);
            if(!isBlockCanApplyToBlockChain){
                return false;
            }
            WriteBatch writeBatch = createWriteBatch(block,BlockChainActionEnum.ADD_BLOCK);
            LevelDBUtil.write(blockChainDB,writeBatch);
            return true;
        }finally {
            writeLock.unlock();
        }
    }

    @Override
    public Block removeTailBlock() throws Exception {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try{
            Block tailBlock = findTailBlock();
            if(tailBlock == null){
                return null;
            }
            WriteBatch writeBatch = createWriteBatch(tailBlock,BlockChainActionEnum.DELETE_BLOCK);
            LevelDBUtil.write(blockChainDB,writeBatch);
            return tailBlock;
        }finally {
            writeLock.unlock();
        }
    }
    //endregion


    //region 区块链提供的通用方法
    @Override
    public Block findTailBlock() throws Exception {
        int blockChainHeight = obtainBlockChainHeight();
        if(blockChainHeight <= 0){
            return null;
        }
        return findBlockByBlockHeight(blockChainHeight);
    }

    @Override
    public int obtainBlockChainHeight() throws Exception {
        byte[] bytesBlockChainHeight = LevelDBUtil.get(blockChainDB, buildBlockChainHeightKey());
        if(bytesBlockChainHeight == null){
            return 0;
        }
        return decodeBlockChainHeight(bytesBlockChainHeight);
    }

    @Override
    public TransactionOutput findUtxoByUtxoUuid(String transactionOutputUUID) throws Exception {
        if(transactionOutputUUID==null || "".equals(transactionOutputUUID)){
            return null;
        }
        byte[] bytesUtxo = LevelDBUtil.get(blockChainDB, buildUnspendTransactionOutputUuidKey(transactionOutputUUID));
        if(bytesUtxo == null){
            return null;
        }
        return EncodeDecode.decodeToTransactionOutput(bytesUtxo);
    }

    @Override
    public Block findBlockByBlockHeight(int blockHeight) throws Exception {
        byte[] bytesBlock = LevelDBUtil.get(blockChainDB, buildBlockHeightKey(blockHeight));
        if(bytesBlock==null){
            return null;
        }
        return EncodeDecode.decodeToBlock(bytesBlock);
    }

    @Override
    public Block findBlockByBlockHash(String blockHash) throws Exception {
        byte[] bytesBlock = LevelDBUtil.get(blockChainDB, buildBlockHashtKey(blockHash));
        if(bytesBlock == null){
            return null;
        }
        return EncodeDecode.decodeToBlock(bytesBlock);
    }

    @Override
    public Transaction findTransactionByTransactionUuid(String transactionUUID) throws Exception {
        byte[] bytesTransaction = LevelDBUtil.get(blockChainDB, buildTransactionUuidKey(transactionUUID));
        if(bytesTransaction==null){
            return null;
        }
        return EncodeDecode.decodeToTransaction(bytesTransaction);
    }
    //endregion

    /**
     * 检测区块是否可以被应用到区块链上
     * 只有一种情况，区块可以被应用到区块链，即: 区块是区块链上的下一个区块
     */
    public boolean isBlockCanApplyToBlockChain(Block block) throws Exception {
        if(block == null){
            throw new BlockChainCoreException("区块校验失败：区块不能为null。");
        }
        if(!isBlcokTransactionSizeLegal(block)){
            logger.error(String.format("区块数据异常，区块里包含的交易数量超过限制值%d。",
                    BlockChainCoreConstants.BLOCK_MAX_TRANSACTION_SIZE));
            return false;
        }
        //校验区块的连贯性
        Block tailBlock = findTailBlock();
        if(tailBlock == null){
            //校验时间
            if(block.getTimestamp() >= System.currentTimeMillis()){
                return false;
            }
            //校验区块Previous Hash
            if(!BlockChainCoreConstants.FIRST_BLOCK_PREVIOUS_HASH.equals(block.getPreviousHash())){
                return false;
            }
            //校验区块高度
            if(BlockChainCoreConstants.FIRST_BLOCK_HEIGHT != block.getHeight()){
                return false;
            }
        } else {
            //校验时间
            if(block.getTimestamp() <= tailBlock.getTimestamp() || block.getTimestamp() >= System.currentTimeMillis()){
                return false;
            }
            //校验区块Hash是否连贯
            if(!tailBlock.getHash().equals(block.getPreviousHash())){
                return false;
            }
            //校验区块高度是否连贯
            if((tailBlock.getHeight()+1) != block.getHeight()){
                return false;
            }
        }

        //校验写入的MerkleRoot是否与计算得来的一致
        if(!BlockUtils.isBlockWriteMerkleRootRight(block)){
            return false;
        }
        //校验写入的Hash是否与计算得来的一致
        if(!BlockUtils.isBlockWriteHashRight(block)){
            return false;
        }

        //校验共识
        boolean isReachConsensus = consensus.isReachConsensus(this,block);
        if(!isReachConsensus){
            return false;
        }

        //校验区块Hash是否已经被使用了
        if(findBlockByBlockHash(block.getHash()) != null){
            logger.error("区块数据异常，区块Hash已经被使用了。");
            return false;
        }

        //校验奖励交易有且只能有一笔
        //挖矿交易笔数
        int minerTransactionNumber = 0;
        for(Transaction tx : block.getTransactions()){
            if(tx.getTransactionType() == TransactionType.MINER){
                minerTransactionNumber++;
            }
        }
        if(minerTransactionNumber == 0){
            logger.error("区块数据异常，没有检测到挖矿奖励交易。");
            return false;
        }
        if(minerTransactionNumber > 1){
            logger.error("区块数据异常，一个区块只能有一笔挖矿奖励。");
            return false;
        }

        //在不同的交易中，UUID(交易的UUID、交易输入UUID、交易输出UUID)不应该被使用两次或是两次以上
        Set<String> uuidSet = new HashSet<>();
        for(Transaction transaction : block.getTransactions()){
            String transactionUUID = transaction.getTransactionUUID();
            if(!saveUuid(uuidSet,transactionUUID)){
                return false;
            }
            List<TransactionInput> inputs = transaction.getInputs();
            if(inputs != null){
                for(TransactionInput transactionInput : inputs) {
                    TransactionOutput unspendTransactionOutput = transactionInput.getUnspendTransactionOutput();
                    String unspendTransactionOutputUUID = unspendTransactionOutput.getTransactionOutputUUID();
                    if(!saveUuid(uuidSet,unspendTransactionOutputUUID)){
                        return false;
                    }
                }
            }
            List<TransactionOutput> outputs = transaction.getOutputs();
            if(outputs != null){
                for(TransactionOutput transactionOutput : outputs) {
                    String transactionOutputUUID = transactionOutput.getTransactionOutputUUID();
                    if(!saveUuid(uuidSet,transactionOutputUUID)){
                        return false;
                    }
                }
            }
        }

        //从交易角度校验每一笔交易
        for(Transaction tx : block.getTransactions()){
            boolean transactionCanAddToNextBlock = isTransactionCanAddToNextBlock(block,tx);
            if(!transactionCanAddToNextBlock){
                logger.error("区块数据异常，交易异常。");
                return false;
            }
        }
        return true;
    }

    public boolean isTransactionCanAddToNextBlock(Block block, Transaction transaction) throws Exception{
        if(block != null && block.getTimestamp() <= transaction.getTimestamp()){
            logger.error("交易校验失败：挖矿的时间应当在交易的时间之后。");
            return false;
        }

        //校验：只从交易对象层面校验，交易中使用的UUID是否有重复
        Set<String> uuidSet = new HashSet<>();
        String transactionUUID = transaction.getTransactionUUID();
        if(!saveUuid(uuidSet,transactionUUID)){
            return false;
        }
        List<TransactionInput> inputs = transaction.getInputs();
        if(inputs != null){
            for(TransactionInput transactionInput : inputs) {
                TransactionOutput unspendTransactionOutput = transactionInput.getUnspendTransactionOutput();
                String unspendTransactionOutputUUID = unspendTransactionOutput.getTransactionOutputUUID();
                if(!saveUuid(uuidSet,unspendTransactionOutputUUID)){
                    return false;
                }
            }
        }
        List<TransactionOutput> outputs = transaction.getOutputs();
        if(outputs != null){
            for(TransactionOutput transactionOutput : outputs) {
                String transactionOutputUUID = transactionOutput.getTransactionOutputUUID();
                if(!saveUuid(uuidSet,transactionOutputUUID)){
                    return false;
                }
            }
        }
        //校验：交易输入UTXO的UUID存在于区块链
        if(inputs != null){
            for(TransactionInput transactionInput : inputs) {
                TransactionOutput unspendTransactionOutput = transactionInput.getUnspendTransactionOutput();
                String unspendTransactionOutputUUID = unspendTransactionOutput.getTransactionOutputUUID();
                TransactionOutput tx = findUtxoByUtxoUuid(unspendTransactionOutputUUID);
                if(tx == null){
                    return false;
                }
            }
        }
        //校验：交易UUID和交易输出的UUID不能已经被区块链占用
        if(isUuidExist(transactionUUID)){
            return false;
        }
        if(outputs != null){
            for(TransactionOutput transactionOutput : outputs) {
                String transactionOutputUUID = transactionOutput.getTransactionOutputUUID();
                if(isUuidExist(transactionOutputUUID)){
                    return false;
                }
            }
        }
        //
        if(inputs != null){
            for(TransactionInput transactionInput : inputs) {
                StringPublicKey stringPublicKey = transactionInput.getStringPublicKey();
                StringAddress stringAddress = transactionInput.getUnspendTransactionOutput().getStringAddress();
                if(!KeyUtil.isStringPublicKeyEqualStringAddress(stringPublicKey,stringAddress)){
                    return false;
                }
            }
        }
        //校验交易输出的金额是否满足区块链系统对金额数字的的强制要求
        if(outputs != null){
            for(TransactionOutput o : outputs) {
                if(!isTransactionAmountLegal(o.getValue())){
                    logger.error(String.format("交易校验失败：交易金额不合法，可能的原因：①交易金额小于限制值%s；" +
                            "②交易金额大于限制值%s；③交易金额小数点位数超过了%d位限制。",
                            BlockChainCoreConstants.TRANSACTION_MIN_AMOUNT,
                            BlockChainCoreConstants.TRANSACTION_MAX_AMOUNT,
                            BlockChainCoreConstants.TRANSACTION_AMOUNT_MAX_DECIMAL_PLACES));
                    return false;
                }
            }
        }

        if(transaction.getTransactionType() == TransactionType.MINER){
            if(!isBlockWriteMineAwardRight(block)){
                logger.error("交易校验失败：挖矿交易的输出金额不正确。");
                return false;
            }
            return true;
        } else if(transaction.getTransactionType() == TransactionType.NORMAL){
            if(inputs==null || inputs.size()==0){
                logger.error("交易校验失败：交易的输入不能为空。不合法的交易。");
                return false;
            }
            BigDecimal inputsValue = TransactionUtil.getInputsValue(transaction);
            BigDecimal outputsValue = TransactionUtil.getOutputsValue(transaction);
            if(inputsValue.compareTo(outputsValue) < 0) {
                logger.error("交易校验失败：交易的输入少于交易的输出。不合法的交易。");
                return false;
            }
            //校验 付款方是同一个用户[公钥] 用户花的钱是自己的钱
            if(!TransactionUtil.isSpendOwnUtxo(transaction)){
                logger.error("交易校验失败：交易的付款方有多个。不合法的交易。");
                return false;
            }
            //校验签名验证
            try{
                if(!TransactionUtil.verifySignature(transaction)) {
                    logger.error("交易校验失败：校验交易签名失败。不合法的交易。");
                    return false;
                }
            }catch (InvalidKeySpecException invalidKeySpecException){
                logger.error("交易校验失败：校验交易签名失败。不合法的交易。",invalidKeySpecException);
                return false;
            }catch (Exception e){
                logger.error("交易校验失败：校验交易签名失败。不合法的交易。",e);
                return false;
            }
            return true;
        } else {
            logger.error("区块数据异常，不能识别的交易类型。");
            return false;
        }
    }

    /**
     * 区块中写入的挖矿奖励是否正确？
     * @param block 被校验挖矿奖励是否正确的区块
     * @return
     */
    private boolean isBlockWriteMineAwardRight(Block block){
        try {
            //校验奖励交易笔数
            int mineAwardTransactionCount = 0;
            for(Transaction tx : block.getTransactions()){
                if(tx.getTransactionType() == TransactionType.MINER){
                    mineAwardTransactionCount++;
                }
            }
            if(mineAwardTransactionCount == 0){
                throw new BlockChainCoreException("区块中没有奖励交易。");
            }
            if(mineAwardTransactionCount > 1){
                throw new BlockChainCoreException("区块中不能有两笔奖励交易。");
            }

            //获取区块中写入的挖矿奖励交易
            Transaction mineAwardTransaction = null;
            for(Transaction tx : block.getTransactions()){
                if(tx.getTransactionType() == TransactionType.MINER){
                    mineAwardTransaction = tx;
                    break;
                }
            }

            List<TransactionInput> inputs = mineAwardTransaction.getInputs();
            if(inputs!=null && inputs.size()!=0){
                logger.error("区块数据异常：挖矿交易的输入只能为空。");
                return false;
            }
            List<TransactionOutput> outputs = mineAwardTransaction.getOutputs();
            if(outputs == null){
                logger.error("区块数据异常：挖矿交易的输出不能为空。");
                return false;
            }
            if(outputs.size() != 1){
                logger.error("区块数据异常：挖矿交易的输出有且只能有一笔。");
                return false;
            }
            //校验正反
            for(TransactionOutput output:outputs){
                if(output.getValue().compareTo(new BigDecimal("0"))<0){
                    logger.error("区块数据异常：挖矿交易的输出不能小于0。");
                    return false;
                }
            }

            //获取区块中写入的挖矿奖励金额
            BigDecimal blockWritedMineAward = new BigDecimal("0");
            for(TransactionOutput output:outputs){
                blockWritedMineAward.add(output.getValue());
            }

            //目标挖矿奖励
            BigDecimal targetMineAward = incentive.mineAward(this, block);
            return targetMineAward.compareTo(blockWritedMineAward) >= 0 ;
        } catch (Exception e){
            logger.error("区块数据异常，挖矿奖励交易不正确。");
            return false;
        }
    }

    //region 数据库相关
    //region 拼装数据库Key的值
    private byte[] buildBlockChainHeightKey() {
        String stringKey = BLOCK_CHAIN_HEIGHT_KEY;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildUuidKey(String uuid) {
        String stringKey = UUID_PREFIX_FLAG + uuid;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildBlockHeightKey(int blockHeight) {
        String stringKey = BLOCK_HEIGHT_PREFIX_FLAG + blockHeight;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildBlockHashtKey(String blockHash) {
        String stringKey = BLOCK_HASH_PREFIX_FLAG + blockHash;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildTransactionUuidKey(String transactionUUID) {
        String stringKey = TRANSACTION_UUID_PREFIX_FLAG + transactionUUID;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildTransactionOutputUuidKey(String transactionOutputUUID) {
        String stringKey = TRANSACTION_OUTPUT_UUID_PREFIX_FLAG + transactionOutputUUID;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildUnspendTransactionOutputUuidKey(String transactionOutputUUID) {
        String stringKey = UNSPEND_TRANSACTION_OUPUT_UUID_PREFIX_FLAG + transactionOutputUUID;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildAddressToTransactionOuputListKey(String address) {
        String stringKey = ADDRESS_TO_TRANSACTION_OUPUT_LIST_KEY_PREFIX_FLAG + address;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    private byte[] buildAddressToUnspendTransactionOuputListKey(String address) {
        String stringKey = ADDRESS_TO_UNSPEND_TRANSACTION_OUPUT_LIST_KEY_PREFIX_FLAG + address;
        return LevelDBUtil.stringToBytes(stringKey);
    }
    //endregion

    //region 拼装WriteBatch
    /**
     * 将区块信息组装成WriteBatch对象
     */
    private WriteBatch createWriteBatch(Block block, BlockChainActionEnum blockChainActionEnum) throws Exception {
        WriteBatch writeBatch = new WriteBatchImpl();
        fillWriteBatch(writeBatch,block,blockChainActionEnum);
        return writeBatch;
    }

    /**
     * 把区块信息组装进WriteBatch对象
     */
    private void fillWriteBatch(WriteBatch writeBatch, Block block, BlockChainActionEnum blockChainActionEnum) throws Exception {
        if(writeBatch == null){
            throw new BlockChainCoreException("参数writeBatch没有初始化");
        }
        if(block == null){
            throw new BlockChainCoreException("区块不能为空");
        }
        if(blockChainActionEnum == null){
            throw new BlockChainCoreException("区块链动作不能为空");
        }
        //更新区块数据
        byte[] blockHeightKey = buildBlockHeightKey(block.getHeight());
        if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
            writeBatch.put(blockHeightKey, EncodeDecode.encode(block));
        }else{
            writeBatch.delete(blockHeightKey);
        }
        byte[] blockHashKey = buildBlockHashtKey(block.getHash());
        if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
            writeBatch.put(blockHashKey, EncodeDecode.encode(block));
        }else{
            writeBatch.delete(blockHashKey);
        }
        //更新区块链的高度
        byte[] blockChainHeightKey = buildBlockChainHeightKey();
        if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
            writeBatch.put(blockChainHeightKey,encodeBlockChainHeight(block.getHeight()));
        }else{
            writeBatch.put(blockChainHeightKey,encodeBlockChainHeight(block.getHeight()-1));
        }

        List<Transaction> transactionList = block.getTransactions();
        if(transactionList != null){
            for(Transaction transaction:transactionList){
                //UUID数据
                byte[] uuidKey = buildUuidKey(transaction.getTransactionUUID());
                //更新交易数据
                byte[] transactionUuidKey = buildTransactionUuidKey(transaction.getTransactionUUID());
                if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
                    writeBatch.put(uuidKey, uuidKey);
                    writeBatch.put(transactionUuidKey, EncodeDecode.encode(transaction));
                } else {
                    writeBatch.delete(uuidKey);
                    writeBatch.delete(transactionUuidKey);
                }
                List<TransactionInput> inputs = transaction.getInputs();
                if(inputs!=null){
                    for(TransactionInput txInput:inputs){
                        //更新UTXO数据
                        TransactionOutput unspendTransactionOutput = txInput.getUnspendTransactionOutput();
                        byte[] unspendTransactionOutputUuidKey = buildUnspendTransactionOutputUuidKey(unspendTransactionOutput.getTransactionOutputUUID());
                        if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
                            writeBatch.delete(unspendTransactionOutputUuidKey);
                        } else {
                            writeBatch.put(unspendTransactionOutputUuidKey,EncodeDecode.encode(unspendTransactionOutput));
                        }
                    }
                }
                List<TransactionOutput> outputs = transaction.getOutputs();
                if(outputs!=null){
                    for(TransactionOutput output:outputs){
                        //UUID数据
                        byte[] uuidKey2 = buildUuidKey(output.getTransactionOutputUUID());
                        //更新所有的交易输出
                        byte[] transactionOutputUuidKey = buildTransactionOutputUuidKey(output.getTransactionOutputUUID());
                        //更新UTXO数据
                        byte[] unspendTransactionOutputUuidKey = buildUnspendTransactionOutputUuidKey(output.getTransactionOutputUUID());
                        if(BlockChainActionEnum.ADD_BLOCK == blockChainActionEnum){
                            writeBatch.put(uuidKey2, uuidKey2);
                            writeBatch.put(transactionOutputUuidKey, EncodeDecode.encode(output));
                            writeBatch.put(unspendTransactionOutputUuidKey, EncodeDecode.encode(output));
                        } else {
                            writeBatch.delete(uuidKey2);
                            writeBatch.delete(transactionOutputUuidKey);
                            writeBatch.delete(unspendTransactionOutputUuidKey);
                        }
                    }
                }
            }
        }
        addAboutAddressWriteBatch(writeBatch,block,blockChainActionEnum);
    }

    private void addAboutAddressWriteBatch(WriteBatch writeBatch, Block block, BlockChainActionEnum blockChainActionEnum) throws Exception {
        Map<String,List<TransactionOutput>> addressUtxoList = new HashMap<>();
        Map<String,List<TransactionOutput>> addressTxoList = new HashMap<>();

        for(Transaction transaction : block.getTransactions()){
            List<TransactionInput> inputs = transaction.getInputs();
            if(inputs!=null){
                for (TransactionInput transactionInput:inputs){
                    TransactionOutput utxo = transactionInput.getUnspendTransactionOutput();
                    List<TransactionOutput> txList = queryUnspendTransactionOuputListByAddress(addressUtxoList,utxo.getStringAddress());
                    //区块数据
                    if(blockChainActionEnum == BlockChainActionEnum.ADD_BLOCK){
                        txList.removeIf(txo -> txo.getTransactionOutputUUID().equals(utxo.getTransactionOutputUUID()));
                    }else{
                        txList.add(utxo);
                    }
                }
            }
            List<TransactionOutput> outputs = transaction.getOutputs();
            for (TransactionOutput transactionOutput:outputs){
                List<TransactionOutput> utxoList = queryUnspendTransactionOuputListByAddress(addressUtxoList,transactionOutput.getStringAddress());
                if(blockChainActionEnum == BlockChainActionEnum.ADD_BLOCK){
                    utxoList.add(transactionOutput);
                }else{
                    utxoList.removeIf(txo -> txo.getTransactionOutputUUID().equals(transactionOutput.getTransactionOutputUUID()));
                }
                List<TransactionOutput> txoList = queryTransactionOuputListByAddress(addressTxoList,transactionOutput.getStringAddress());
                if(blockChainActionEnum == BlockChainActionEnum.ADD_BLOCK){
                    txoList.add(transactionOutput);
                }else{
                    txoList.removeIf(txo -> txo.getTransactionOutputUUID().equals(transactionOutput.getTransactionOutputUUID()));
                }
            }
        }

        for(Map.Entry<String,List<TransactionOutput>> entry:addressUtxoList.entrySet()){
            String address = entry.getKey();
            List<TransactionOutput> utxoList = entry.getValue();
            writeBatch.put(buildAddressToUnspendTransactionOuputListKey(address),EncodeDecode.encode(utxoList));
        }

        for(Map.Entry<String,List<TransactionOutput>> entry:addressTxoList.entrySet()){
            String address = entry.getKey();
            List<TransactionOutput> txoList = entry.getValue();
            writeBatch.put(buildAddressToTransactionOuputListKey(address),EncodeDecode.encode(txoList));
        }
    }

    private List<TransactionOutput> queryUnspendTransactionOuputListByAddress(Map<String, List<TransactionOutput>> addressUtxoList, StringAddress stringAddress) throws Exception {
        List<TransactionOutput> transactionOutputList = addressUtxoList.get(stringAddress.getValue());
        if(transactionOutputList == null){
            transactionOutputList = querUnspendTransactionOuputListByAddress(stringAddress);
            if(transactionOutputList == null){
                transactionOutputList = new ArrayList<>();
            }
            addressUtxoList.put(stringAddress.getValue(),transactionOutputList);
        }
        return transactionOutputList;
    }

    private List<TransactionOutput> queryTransactionOuputListByAddress(Map<String, List<TransactionOutput>> addressUtxoList, StringAddress stringAddress) throws Exception {
        List<TransactionOutput> transactionOutputList = addressUtxoList.get(stringAddress.getValue());
        if(transactionOutputList == null){
            transactionOutputList = queryTransactionOuputListByAddress(stringAddress);
            if(transactionOutputList == null){
                transactionOutputList = new ArrayList<>();
            }
            addressUtxoList.put(stringAddress.getValue(),transactionOutputList);
        }
        return transactionOutputList;
    }

    public List<TransactionOutput> querUnspendTransactionOuputListByAddress(StringAddress stringAddress) throws Exception {
        byte[] byteTxo = LevelDBUtil.get(blockChainDB, buildAddressToUnspendTransactionOuputListKey(stringAddress.getValue()));
        if(byteTxo==null){
            return null;
        }else {
            List<TransactionOutput> transactionOutputList = EncodeDecode.decodeToTransactionOutputList(byteTxo);
            return transactionOutputList;
        }
    }

    public List<TransactionOutput> queryTransactionOuputListByAddress(StringAddress stringAddress) throws Exception {
        byte[] byteTxo = LevelDBUtil.get(blockChainDB, buildAddressToTransactionOuputListKey(stringAddress.getValue()));
        if(byteTxo == null){
            return null;
        }else {
            List<TransactionOutput> transactionOutputList = EncodeDecode.decodeToTransactionOutputList(byteTxo);
            return transactionOutputList;
        }
    }
    //endregion
    //endregion


    /**
     * UUID是否已经存在于区块链之中？
     * @param uuid uuid
     */
    private boolean isUuidExist(String uuid){
        byte[] bytesUuid = LevelDBUtil.get(blockChainDB, buildUuidKey(uuid));
        return bytesUuid != null;
    }

    /**
     * 将UUID保存进Set
     * 如果UUID格式不正确，则返回false
     * 如果Set里已经包含了UUID，返回false
     * 否则，将UUID保存进Set，返回true
     */
    private boolean saveUuid(Set<String> uuidSet, String uuid) {
        if(!UuidUtil.isUuidFormatRight(uuid)) {
            return false;
        }
        if(uuidSet.contains(uuid)){
            return false;
        } else {
            uuidSet.add(uuid);
        }
        return true;
    }
    private int decodeBlockChainHeight(byte[] bytesBlockChainHeight){
        String strBlockChainHeight = LevelDBUtil.bytesToString(bytesBlockChainHeight);
        Integer intBlockChainHeight = Integer.valueOf(strBlockChainHeight);
        return intBlockChainHeight;
    }
    private byte[] encodeBlockChainHeight(int blockChainHeight){
        return LevelDBUtil.stringToBytes(String.valueOf(blockChainHeight));
    }
}