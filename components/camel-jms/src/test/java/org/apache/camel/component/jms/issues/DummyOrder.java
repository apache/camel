package org.apache.camel.component.jms.issues;

import java.io.Serializable;

/**
 * Model for unit test.
 */
public class DummyOrder implements Serializable {

    private long orderId;
    private long itemId;
    private int quantity;

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DummyOrder that = (DummyOrder)o;

        if (itemId != that.itemId) {
            return false;
        }
        if (orderId != that.orderId) {
            return false;
        }
        if (quantity != that.quantity) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result;
        result = (int)(orderId ^ (orderId >>> 32));
        result = 31 * result + (int)(itemId ^ (itemId >>> 32));
        result = 31 * result + quantity;
        return result;
    }

    public String toString() {
        return "DummyOrder{" +
            "orderId=" + orderId +
            ", itemId=" + itemId +
            ", quantity=" + quantity +
            '}';
    }
}
