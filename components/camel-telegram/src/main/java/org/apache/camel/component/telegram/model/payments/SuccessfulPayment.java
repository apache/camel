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

/**
 * This object contains basic information about a successful payment.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#successfulpayment">https://core.telegram.org/bots/api#successfulpayment</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuccessfulPayment implements Serializable {

    private static final long serialVersionUID = 3244496332391255696L;

    private String currency;

    @JsonProperty("total_amount")
    private Integer totalAmount;

    @JsonProperty("invoice_payload")
    private String invoicePayload;

    @JsonProperty("subscription_expiration_date")
    private Integer subscriptionExpirationDate;

    @JsonProperty("is_recurring")
    private boolean isRecurring;

    @JsonProperty("is_first_recurring")
    private boolean isFirstRecurring;

    @JsonProperty("shipping_option_id")
    private String shippingOptionId;

    @JsonProperty("order_info")
    private OrderInfo orderInfo;

    @JsonProperty("telegram_payment_charge_id")
    private String telegramPaymentChargeId;

    @JsonProperty("provider_payment_charge_id")
    private String providerPaymentChargeId;

    public SuccessfulPayment(String currency, Integer totalAmount, String invoicePayload,
                             Integer subscriptionExpirationDate, boolean isRecurring, boolean isFirstRecurring,
                             String shippingOptionId, OrderInfo orderInfo, String telegramPaymentChargeId,
                             String providerPaymentChargeId) {
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.invoicePayload = invoicePayload;
        this.subscriptionExpirationDate = subscriptionExpirationDate;
        this.isRecurring = isRecurring;
        this.isFirstRecurring = isFirstRecurring;
        this.shippingOptionId = shippingOptionId;
        this.orderInfo = orderInfo;
        this.telegramPaymentChargeId = telegramPaymentChargeId;
        this.providerPaymentChargeId = providerPaymentChargeId;
    }

    public SuccessfulPayment() {
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

    public Integer getSubscriptionExpirationDate() {
        return subscriptionExpirationDate;
    }

    public void setSubscriptionExpirationDate(Integer subscriptionExpirationDate) {
        this.subscriptionExpirationDate = subscriptionExpirationDate;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }

    public boolean isFirstRecurring() {
        return isFirstRecurring;
    }

    public void setFirstRecurring(boolean firstRecurring) {
        isFirstRecurring = firstRecurring;
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

    public String getTelegramPaymentChargeId() {
        return telegramPaymentChargeId;
    }

    public void setTelegramPaymentChargeId(String telegramPaymentChargeId) {
        this.telegramPaymentChargeId = telegramPaymentChargeId;
    }

    public String getProviderPaymentChargeId() {
        return providerPaymentChargeId;
    }

    public void setProviderPaymentChargeId(String providerPaymentChargeId) {
        this.providerPaymentChargeId = providerPaymentChargeId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SuccessfulPayment{");
        sb.append("currency='").append(currency).append('\'');
        sb.append(", totalAmount=").append(totalAmount);
        sb.append(", invoicePayload=").append(invoicePayload);
        sb.append(", subscriptionExpirationDate=").append(subscriptionExpirationDate);
        sb.append(", isRecurring=").append(isRecurring);
        sb.append(", isFirstRecurring=").append(isFirstRecurring);
        sb.append(", shippingOptionId='").append(shippingOptionId).append('\'');
        sb.append(", orderInfo=").append(orderInfo);
        sb.append(", telegramPaymentChargeId='").append(telegramPaymentChargeId).append('\'');
        sb.append(", providerPaymentChargeId='").append(providerPaymentChargeId).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
