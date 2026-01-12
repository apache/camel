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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.model.Gift;
import org.apache.camel.component.telegram.model.PaidMedia;
import org.apache.camel.component.telegram.model.User;

/**
 * Describes a transaction with a user.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartneruser">https://core.telegram.org/bots/api#transactionpartneruser</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPartnerUser extends TransactionPartner {

    @Serial
    private static final long serialVersionUID = -8469984079835620034L;

    /**
     * Type of the transaction, currently one of "invoice_payment", "paid_media_payment", "gift_purchase",
     * "premium_purchase", "business_account_transfer".
     */
    @JsonProperty("transaction_type")
    private String transactionType;

    /**
     * Information about the user.
     */
    private User user;

    /**
     * Information about the affiliate that received a commission via this transaction.
     */
    private AffiliateInfo affiliate;

    /**
     * Bot-specified invoice payload.
     */
    @JsonProperty("invoice_payload")
    private String invoicePayload;

    /**
     * The duration of the paid subscription.
     */
    @JsonProperty("subscription_period")
    private Integer subscriptionPeriod;

    /**
     * Information about the paid media bought by the user.
     */
    @JsonProperty("paid_media")
    private List<PaidMedia> paidMedia;

    /**
     * Bot-specified paid media payload.
     */
    @JsonProperty("paid_media_payload")
    private String paidMediaPayload;

    /**
     * The gift sent to the user by the bot.
     */
    private Gift gift;

    /**
     * Number of months the gifted Telegram Premium subscription will be active for.
     */
    @JsonProperty("premium_subscription_duration")
    private Integer premiumSubscriptionDuration;

    public TransactionPartnerUser() {
        super("user");
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AffiliateInfo getAffiliate() {
        return affiliate;
    }

    public void setAffiliate(AffiliateInfo affiliate) {
        this.affiliate = affiliate;
    }

    public String getInvoicePayload() {
        return invoicePayload;
    }

    public void setInvoicePayload(String invoicePayload) {
        this.invoicePayload = invoicePayload;
    }

    public Integer getSubscriptionPeriod() {
        return subscriptionPeriod;
    }

    public void setSubscriptionPeriod(Integer subscriptionPeriod) {
        this.subscriptionPeriod = subscriptionPeriod;
    }

    public List<PaidMedia> getPaidMedia() {
        return paidMedia;
    }

    public void setPaidMedia(List<PaidMedia> paidMedia) {
        this.paidMedia = paidMedia;
    }

    public String getPaidMediaPayload() {
        return paidMediaPayload;
    }

    public void setPaidMediaPayload(String paidMediaPayload) {
        this.paidMediaPayload = paidMediaPayload;
    }

    public Gift getGift() {
        return gift;
    }

    public void setGift(Gift gift) {
        this.gift = gift;
    }

    public Integer getPremiumSubscriptionDuration() {
        return premiumSubscriptionDuration;
    }

    public void setPremiumSubscriptionDuration(Integer premiumSubscriptionDuration) {
        this.premiumSubscriptionDuration = premiumSubscriptionDuration;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionPartnerUser{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", transactionType='").append(transactionType).append('\'');
        sb.append(", user=").append(user);
        sb.append(", affiliate=").append(affiliate);
        sb.append(", invoicePayload='").append(invoicePayload).append('\'');
        sb.append(", subscriptionPeriod=").append(subscriptionPeriod);
        sb.append(", paidMedia=").append(paidMedia);
        sb.append(", paidMediaPayload='").append(paidMediaPayload).append('\'');
        sb.append(", gift=").append(gift);
        sb.append(", premiumSubscriptionDuration=").append(premiumSubscriptionDuration);
        sb.append('}');
        return sb.toString();
    }
}
