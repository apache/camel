/*
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
package org.apache.camel.component.jms;

import java.io.Serializable;

/**
 * A simple POJO for testing
 */
public class PurchaseOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    private String product;
    private double amount;

    public PurchaseOrder(String product, double amount) {
        this.product = product;
        this.amount = amount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (this.getClass() != other.getClass()) {
            return false;
        }
        PurchaseOrder that = (PurchaseOrder) other;
        return this.product.equals(that.product) && this.amount == that.amount;
    }

    @Override
    public int hashCode() {
        return product.hashCode() * 37 + (int) Math.round(amount);
    }

    @Override
    public String toString() {
        return "PurchaseOrder[" + product + " x " + amount + "]";
    }

    public double getAmount() {
        return amount;
    }

    public String getProduct() {
        return product;
    }
}
