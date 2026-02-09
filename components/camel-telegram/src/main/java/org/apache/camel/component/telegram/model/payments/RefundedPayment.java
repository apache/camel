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

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object contains basic information about a refunded payment.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#refundedpayment">https://core.telegram.org/bots/api#refundedpayment</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundedPayment implements Serializable {

    @Serial
    private static final long serialVersionUID = 7527872288612186433L;

    /**
     * Three-letter ISO 4217 currency code, or "XTR" for payments in Telegram Stars. Currently, always "XTR".
     */
    private String currency;

    /**
     * Total refunded price in the smallest units of the currency (integer, not float/double).
     */
    @JsonProperty("total_amount")
    private Integer totalAmount;

    /**
     * Bot-specified invoice payload.
     */
    @JsonProperty("invoice_payload")
    private String invoicePayload;

    /**
     * Telegram payment identifier.
     */
    @JsonProperty("telegram_payment_charge_id")
    private String telegramPaymentChargeId;

    /**
     * Provider payment identifier.
     */
    @JsonProperty("provider_payment_charge_id")
    private String providerPaymentChargeId;

    public RefundedPayment() {
    }

    public RefundedPayment(String currency, Integer totalAmount, String invoicePayload, String telegramPaymentChargeId,
                           String providerPaymentChargeId) {
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.invoicePayload = invoicePayload;
        this.telegramPaymentChargeId = telegramPaymentChargeId;
        this.providerPaymentChargeId = providerPaymentChargeId;
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
        final StringBuilder sb = new StringBuilder("RefundedPayment{");
        sb.append("currency='").append(currency).append('\'');
        sb.append(", totalAmount=").append(totalAmount);
        sb.append(", invoicePayload='").append(invoicePayload).append('\'');
        sb.append(", telegramPaymentChargeId='").append(telegramPaymentChargeId).append('\'');
        sb.append(", providerPaymentChargeId='").append(providerPaymentChargeId).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
