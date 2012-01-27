/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms.issues;

import java.io.Serializable;

/**
 * Model for unit test.
 */
public class DummyOrder implements Serializable {

    private static final long serialVersionUID = 1L;
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
        return "DummyOrder{"
            + "orderId="
            + orderId
            + ", itemId="
            + itemId
            + ", quantity="
            + quantity
            + '}';
    }
}
