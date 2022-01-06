package com.fiture.plugin.audit.action;

/**
 * @author yinjun
 * @create 2021/4/7
 */
public enum StockBillTypeEnum {
    PURCHASE_IN("采购入库", false, true, true),
    TRANSFER_IN("调拨入库", false, true, true),
    REFUND_IN("退货入库", false, true, true),
    REJECT_IN("拒收入库", false, true, true),
    ON_WAY_IN("在途入库", false, true, false),
    STOCK_CHECK_IN("盘点入库", false, true, true),
    OTHERS_IN("其他入库", false, true, true),
    ON_WAY_OUT("在途出库", true, false, false),
    TRANSFER_OUT("调拨出库", true, false, true),
    TRADE_OUT("销售出库", true, false, true),
    STOCK_CHECK_OUT("盘点出库", true, false, true),
    OTHERS_OUT("其他出库", true, false, true),
    CRAWL_IN("生产入库", false, true, true),
    CRAWL_OUT("发货出库", true, false, true),
    BALANCE_IN("平账入库", false, true, true),
    BALANCE_OUT("平账出库", true, false, true),
    CRAWL_PRODUCE("投料生产", false, false, true),
    ;

    private String desc;
    private boolean stockOut;
    private boolean stockIn;
    private boolean visible;

    StockBillTypeEnum(String desc, boolean stockOut, boolean stockIn, boolean visible) {
        this.desc = desc;
        this.stockOut = stockOut;
        this.stockIn = stockIn;
        this.visible = visible;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isStockOut() {
        return stockOut;
    }

    public boolean isStockIn() {
        return stockIn;
    }

    public boolean isVisible() {
        return visible;
    }
}
