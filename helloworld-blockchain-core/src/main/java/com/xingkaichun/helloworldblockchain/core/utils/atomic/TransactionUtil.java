package com.xingkaichun.helloworldblockchain.core.utils.atomic;

import com.xingkaichun.helloworldblockchain.model.key.StringPrivateKey;
import com.xingkaichun.helloworldblockchain.model.key.StringPublicKey;
import com.xingkaichun.helloworldblockchain.model.transaction.Transaction;
import com.xingkaichun.helloworldblockchain.model.transaction.TransactionInput;
import com.xingkaichun.helloworldblockchain.model.transaction.TransactionOutput;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class TransactionUtil {

    /**
     * 交易输入总额
     */
    public static BigDecimal getInputsValue(List<TransactionInput> inputs) {
        BigDecimal total = new BigDecimal("0");
        for(TransactionInput i : inputs) {
            if(i.getUnspendTransactionOutput() == null) continue; //if Transaction can't be found skip it, This behavior may not be optimal.
            total = total.add(i.getUnspendTransactionOutput().getValue());
        }
        return total;
    }

    /**
     * 交易输出总额
     */
    public static BigDecimal getOutputsValue(List<TransactionOutput> outputs) {
        BigDecimal total = new BigDecimal("0");
        for(TransactionOutput o : outputs) {
            total = total.add(o.getValue());

        }
        return total;
    }

    /**
     * 交易输入总额
     */
    public static BigDecimal getInputsValue(Transaction transaction) {
        return getInputsValue(transaction.getInputs());
    }

    /**
     * 交易输出总额
     */
    public static BigDecimal getOutputsValue(Transaction transaction) {
        return getOutputsValue(transaction.getOutputs());
    }


    /**
     * 用于签名的数据数据
     * @return
     */
    public static String signatureData(Transaction transaction) throws Exception {
        String data = signatureData(transaction.getTimestamp(),transaction.getTransactionUUID(),getInputUtxoIds(transaction),getOutpuUtxoIds(transaction));
        return data;
    }

    /**
     * 用于签名的数据数据
     * @return
     */
    public static String signatureData(long timestamp,String transactionUUID,List<String> inputUtxoUuidList,List<String> outputUtxoUuidList) throws Exception {
        String inputs = "";
        if(inputUtxoUuidList != null){
            for(String input:inputUtxoUuidList){
                inputs += input;
            }
        }
        String outputs = "";
        if(outputUtxoUuidList != null){
            for(String output:outputUtxoUuidList){
                outputs += output;
            }
        }
        String data = timestamp + transactionUUID + inputs + outputs;
        String sha256Data = CipherUtil.applySha256(data);
        return sha256Data;
    }

    public static StringPublicKey getSenderStringPublicKey(Transaction transaction) {
        List<TransactionInput> inputs = transaction.getInputs();
        if(inputs == null || inputs.size() == 0){
            return null;
        }
        StringPublicKey senderStringPublicKey = inputs.get(0).getStringPublicKey();
        return senderStringPublicKey;
    }

    /**
     * 交易签名
     */
    public static String signature(Transaction transaction, StringPrivateKey stringPrivateKey) throws Exception {
        PrivateKey privateKey = KeyUtil.convertStringPrivateKeyToPrivateKey(stringPrivateKey);
        byte[] bytesSignature = CipherUtil.applyECDSASig(privateKey,signatureData(transaction));
        String strSignature = Base64.getEncoder().encodeToString(bytesSignature);
        return strSignature;
    }

    /**
     * 签名验证
     */
    public static boolean verifySignature(Transaction transaction) throws Exception {
        StringPublicKey senderStringPublicKey = getSenderStringPublicKey(transaction);
        PublicKey publicKey = KeyUtil.convertStringPublicKeyToPublicKey(senderStringPublicKey);
        String strSignature = transaction.getSignature();
        byte[] bytesSignature = Base64.getDecoder().decode(strSignature);
        return CipherUtil.verifyECDSASig(publicKey,signatureData(transaction),bytesSignature);
    }

    public static List<String> getInputUtxoIds(Transaction transaction){
        List<String> ids = new ArrayList<>();
        if(transaction.getInputs()==null){return ids;}
        for(TransactionInput transactionInput:transaction.getInputs()){
            ids.add(transactionInput.getUnspendTransactionOutput().getTransactionOutputUUID());
        }
        return ids;
    }

    public static List<String> getOutpuUtxoIds(Transaction transaction){
        List<String> ids = new ArrayList<>();
        List<TransactionOutput> outputs = transaction.getOutputs();
        if(outputs==null){
            return ids;
        }
        for(TransactionOutput transactionOutput:outputs){
            ids.add(transactionOutput.getTransactionOutputUUID());
        }
        return ids;
    }

    public static boolean isSpendOwnUtxo(Transaction transaction){
        StringPublicKey senderStringPublicKey = getSenderStringPublicKey(transaction);
        List<TransactionInput> inputs = transaction.getInputs();
        for(TransactionInput input:inputs){
            boolean eq = senderStringPublicKey.getValue().equals(input.getStringPublicKey().getValue());
            if(!eq){
                return false;
            }
        }
        return true;
    }
}
