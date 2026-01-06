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
 * This object contains information about an incoming pre-checkout query.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#precheckoutquery">https://core.telegram.org/bots/api#precheckoutquery</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreCheckoutQuery implements Serializable {

    private static final long serialVersionUID = -1840129234669542264L;

    private String id;
    private User from;
    private String currency;

    @JsonProperty("total_amount")
    private Integer totalAmount;

    @JsonProperty("invoice_payload")
    private String invoicePayload;

    @JsonProperty("shipping_option_id")
    private String shippingOptionId;

    @JsonProperty("order_info")
    private OrderInfo orderInfo;

    public PreCheckoutQuery() {
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getInvoicePayload() {
        return invoicePayload;
    }

    public void setInvoicePayload(String invoicePayload) {
        this.invoicePayload = invoicePayload;
    }

    public String getShippingOptionId() {
        return shippingOptionId;
    }

    public void setShippingOptionId(String shippingOptionId) {
        this.shippingOptionId = shippingOptionId;
    }

    public OrderInfo getOrderInfo() {
        return orderInfo;
    }

    public void setOrderInfo(OrderInfo orderInfo) {
        this.orderInfo = orderInfo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PreCheckoutQuery{");
        sb.append("id='").append(id).append('\'');
        sb.append(", from=").append(from);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", totalAmount=").append(totalAmount);
        sb.append(", invoicePayload=").append(invoicePayload);
        sb.append(", shippingOptionId=").append(shippingOptionId);
        sb.append(", orderInfo=").append(orderInfo);
        sb.append('}');
        return sb.toString();
    }
}
