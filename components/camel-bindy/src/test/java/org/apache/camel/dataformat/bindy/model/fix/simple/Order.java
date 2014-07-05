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
package org.apache.camel.dataformat.bindy.model.fix.simple;

import java.util.Date;

import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;

@Message(keyValuePairSeparator = "=", pairSeparator = "\\u0001", type = "FIX", version = "4.1")
public class Order {

    @Link
    Header header;

    @Link
    Trailer trailer;

    @KeyValuePairField(tag = 1)
    // Client reference
    private String account;

    @KeyValuePairField(tag = 11)
    // Order reference
    private String clOrdId;

    @KeyValuePairField(tag = 22)
    // Fund ID type (Sedol, ISIN, ...)
    private String iDSource;

    @KeyValuePairField(tag = 48)
    // Fund code
    private String securityId;

    @KeyValuePairField(tag = 54)
    // Movement type ( 1 = Buy, 2 = sell)
    private String side;

    @KeyValuePairField(tag = 58)
    // Free text
    private String text;

    @KeyValuePairField(tag = 777, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "GMT-3")
    // created
    private Date created;

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public Trailer getTrailer() {
        return trailer;
    }

    public void setTrailer(Trailer trailer) {
        this.trailer = trailer;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public String getIDSource() {
        return iDSource;
    }

    public void setIDSource(String source) {
        this.iDSource = source;
    }

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public String toString() {

        return Order.class.getName() + " --> 1: " + this.account + ", 11: " + this.clOrdId + ", 22: " + this.iDSource + ", 48: " + this.securityId + ", 54: " + this.side
               + ", 58: " + this.text + ", 777: " + this.created;

    }

}
