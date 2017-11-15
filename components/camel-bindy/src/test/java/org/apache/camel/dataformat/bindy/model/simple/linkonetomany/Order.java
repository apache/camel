package org.apache.camel.dataformat.bindy.model.simple.linkonetomany;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.OneToMany;

import java.util.List;

@CsvRecord(separator = ",", generateHeaderColumns = true)
public class Order {

    @DataField(pos = 1)
    private int orderNumber;

    @DataField(pos = 2)
    private String customerName;

    @OneToMany
    private List<OrderItem> items;

    public int getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
