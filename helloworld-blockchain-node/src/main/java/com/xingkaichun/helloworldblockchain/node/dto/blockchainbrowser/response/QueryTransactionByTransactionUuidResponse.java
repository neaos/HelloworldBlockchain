package com.xingkaichun.helloworldblockchain.node.dto.blockchainbrowser.response;

import com.xingkaichun.helloworldblockchain.dto.TransactionDTO;
import com.xingkaichun.helloworldblockchain.dto.TransactionTypeDTO;
import lombok.Data;

@Data
public class QueryTransactionByTransactionUuidResponse {

    private TransactionDTO transactionDTO;
}
