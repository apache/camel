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
package org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.Link;

@CsvRecord(separator = ",")
public class Order {

    @DataField(pos = 0)
    private int orderNr;

    @Link
    private Client client;

    @DataField(pos = 4)
    private String isinCode;

    @DataField(name = "Name", pos = 5)
    private String instrumentName;

    @DataField(pos = 6, precision = 2)
    private BigDecimal amount;

    @DataField(pos = 7)
    private String currency;

    @DataField(pos = 8, pattern = "dd-MM-yyyy")
    private Date orderDate;

    public int getOrderNr() {
        return orderNr;
    }

    public void setOrderNr(int orderNr) {
        this.orderNr = orderNr;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getIsinCode() {
        return isinCode;
    }

    public void setIsinCode(String code) {
        isinCode = code;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    @Override
    public String toString() {
        return ("Model : " + Order.class.getName() + " : " + this.getOrderNr() + ", " + this.getAmount().toString() + ", " + this.getIsinCode() + ", "
                + this.getInstrumentName() + ", " + this.getCurrency())
               + ", " + this.getClient() + "," + this.getOrderDate();
    }

}
