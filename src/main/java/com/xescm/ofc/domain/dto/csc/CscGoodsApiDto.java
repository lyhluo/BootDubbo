package com.xescm.ofc.domain.dto.csc;

import java.io.Serializable;

/**
 * Created by Liqd on 2016/11/22.
 */
public class CscGoodsApiDto implements Serializable {

    /**
     * 客户id
     */
    private String customerId;
    /**
     * 货品编码
     */
    private String goodsCode;
    /**
     * 货品名称
     */
    private String goodsName;
    /**
     * 货品种类id
     */
    private String goodsTypeId;
    /**
     * 货品二级种类id
     */
    private String goodsTypeSonId;
    /**
     * 条形码code
     */
    private String barCode;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getGoodsCode() {
        return goodsCode;
    }

    public void setGoodsCode(String goodsCode) {
        this.goodsCode = goodsCode;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public String getGoodsTypeId() {
        return goodsTypeId;
    }

    public void setGoodsTypeId(String goodsTypeId) {
        this.goodsTypeId = goodsTypeId;
    }

    public String getGoodsTypeSonId() {
        return goodsTypeSonId;
    }

    public void setGoodsTypeSonId(String goodsTypeSonId) {
        this.goodsTypeSonId = goodsTypeSonId;
    }

    public String getBarCode() {
        return barCode;
    }

    public void setBarCode(String barCode) {
        this.barCode = barCode;
    }
}
