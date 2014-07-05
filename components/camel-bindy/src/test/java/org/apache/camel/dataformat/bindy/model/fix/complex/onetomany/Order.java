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
package org.apache.camel.dataformat.bindy.model.fix.complex.onetomany;

import java.util.Date;
import java.util.List;

import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.annotation.OneToMany;

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

    @KeyValuePairField(tag = 58)
    // Free text
    private String text;

    @KeyValuePairField(tag = 777, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "GMT-3")
    // created
    private Date created;

    @OneToMany(mappedTo = "org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Security")
    private List<Security> securities;

    public List<Security> getSecurities() {
        return securities;
    }

    public void setSecurities(List<Security> securities) {
        this.securities = securities;
    }

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
        StringBuilder temp = new StringBuilder();
        temp.append(Order.class.getName() + " --> 1: " + this.account + ", 11: " + this.clOrdId
                + ", 58: " + this.text + ", 777: " + this.created);
        temp.append("\r");

        if (this.securities != null) {
            for (Security sec : this.securities) {
                temp.append(sec.toString());
                temp.append("\r");
            }
        }
        return temp.toString();
    }

}
