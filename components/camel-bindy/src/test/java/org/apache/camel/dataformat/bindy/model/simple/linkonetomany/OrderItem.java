package org.apache.camel.dataformat.bindy.model.simple.linkonetomany;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", generateHeaderColumns = true)
public class OrderItem {

    @DataField(pos = 3)
    private String sku;

    @DataField(pos = 4)
    private int quantity;

    @DataField(pos = 5)
    private int unitPrice;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }
}
