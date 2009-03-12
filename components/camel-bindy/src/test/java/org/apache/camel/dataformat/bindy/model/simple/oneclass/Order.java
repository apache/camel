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
package org.apache.camel.dataformat.bindy.model.simple.oneclass;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",")
public class Order {

    @DataField(pos = 0)
    private int orderNr;

    @DataField(pos = 1)
    private String clientNr;

    @DataField(pos = 2)
    private String firstName;

    @DataField(pos = 3)
    private String lastName;

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

    public String getClientNr() {
        return clientNr;
    }

    public void setClientNr(String clientNr) {
        this.clientNr = clientNr;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getOrderNr() {
        return orderNr;
    }

    public void setOrderNr(int orderNr) {
        this.orderNr = orderNr;
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
        return "Model : " + Order.class.getName() + " : " + 
            String.valueOf(this.getOrderNr()) + ", " + 
            String.valueOf(this.getAmount()) + ", " + 
            String.valueOf(this.getIsinCode()) + ", " + 
            String.valueOf(this.getInstrumentName()) + ", " + 
            String.valueOf(this.getCurrency()) + ", " + 
            String.valueOf(this.getClientNr()) + ", " + 
            String.valueOf(this.getFirstName()) + ", " + 
            String.valueOf(this.getLastName()) + ", " + 
            String.valueOf(this.getOrderDate());
    }
}
