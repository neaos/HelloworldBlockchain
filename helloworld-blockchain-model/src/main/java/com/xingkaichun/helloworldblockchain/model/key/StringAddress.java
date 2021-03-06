package com.xingkaichun.helloworldblockchain.model.key;

import lombok.Data;

import java.io.Serializable;

/**
 * 地址
 */
@Data
public class StringAddress implements Serializable {

    private String value;

    public StringAddress(String value) {
        this.value = value;
    }
}
