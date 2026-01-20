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

package org.apache.camel.component.telegram.model.payments;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.model.User;

/**
 * This object contains information about an incoming shipping query.
 *
 * @see <a href= "https://core.telegram.org/bots/api#shippingquery">https://core.telegram.org/bots/api#shippingquery</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingQuery implements Serializable {

    private static final long serialVersionUID = 1138356833926458198L;

    private String id;
    private User from;

    @JsonProperty("invoice_payload")
    private String invoicePayload;

    @JsonProperty("shipping_address")
    private ShippingAddress shippingAddress;

    public ShippingQuery() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public String getInvoicePayload() {
        return invoicePayload;
    }

    public void setInvoicePayload(String invoicePayload) {
        this.invoicePayload = invoicePayload;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(ShippingAddress shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ShippingQuery{");
        sb.append("id='").append(id).append('\'');
        sb.append(", from=").append(from);
        sb.append(", invoicePayload='").append(invoicePayload).append('\'');
        sb.append(", shippingAddress=").append(shippingAddress);
        sb.append('}');
        return sb.toString();
    }
}
