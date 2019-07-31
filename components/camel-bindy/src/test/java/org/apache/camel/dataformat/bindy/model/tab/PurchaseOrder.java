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
package org.apache.camel.dataformat.bindy.model.tab;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = "\t", crlf = "UNIX")
public class PurchaseOrder {

    @DataField(pos = 1)
    private int id;

    @DataField(pos = 2)
    private String name;

    @DataField(pos = 3)
    private int amount;

    @DataField(pos = 4, required = false)
    private String orderText;

    @DataField(pos = 5, required = false)
    private String salesRef;

    @DataField(pos = 6, required = false)
    private String customerRef;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getOrderText() {
        return orderText;
    }

    public void setOrderText(String text) {
        this.orderText = text;
    }

    public String getSalesRef() {
        return salesRef;
    }

    public void setSalesRef(String salesRef) {
        this.salesRef = salesRef;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public void setCustomerRef(String customerRef) {
        this.customerRef = customerRef;
    }
}
