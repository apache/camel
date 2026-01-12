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
import org.apache.camel.component.telegram.model.Chat;
import org.apache.camel.component.telegram.model.User;

/**
 * Contains information about the affiliate that received a commission via this transaction.
 *
 * @see <a href="https://core.telegram.org/bots/api#affiliateinfo">https://core.telegram.org/bots/api#affiliateinfo</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AffiliateInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = -5861679265904931723L;

    /**
     * The bot or the user that received an affiliate commission if it was received by a bot or a user.
     */
    @JsonProperty("affiliate_user")
    private User affiliateUser;

    /**
     * The chat that received an affiliate commission if it was received by a chat.
     */
    @JsonProperty("affiliate_chat")
    private Chat affiliateChat;

    /**
     * The number of Telegram Stars received by the affiliate for each 1000 Telegram Stars received by the bot from
     * referred users.
     */
    @JsonProperty("commission_per_mille")
    private Integer commissionPerMille;

    /**
     * Integer amount of Telegram Stars received by the affiliate from the transaction, rounded to 0; can be negative
     * for refunds.
     */
    private Integer amount;

    /**
     * The number of 1/1000000000 shares of Telegram Stars received by the affiliate; from -999999999 to 999999999; can
     * be negative for refunds.
     */
    @JsonProperty("nanostar_amount")
    private Integer nanostarAmount;

    public AffiliateInfo() {
    }

    public AffiliateInfo(User affiliateUser, Chat affiliateChat, Integer commissionPerMille, Integer amount,
                         Integer nanostarAmount) {
        this.affiliateUser = affiliateUser;
        this.affiliateChat = affiliateChat;
        this.commissionPerMille = commissionPerMille;
        this.amount = amount;
        this.nanostarAmount = nanostarAmount;
    }

    public User getAffiliateUser() {
        return affiliateUser;
    }

    public void setAffiliateUser(User affiliateUser) {
        this.affiliateUser = affiliateUser;
    }

    public Chat getAffiliateChat() {
        return affiliateChat;
    }

    public void setAffiliateChat(Chat affiliateChat) {
        this.affiliateChat = affiliateChat;
    }

    public Integer getCommissionPerMille() {
        return commissionPerMille;
    }

    public void setCommissionPerMille(Integer commissionPerMille) {
        this.commissionPerMille = commissionPerMille;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public Integer getNanostarAmount() {
        return nanostarAmount;
    }

    public void setNanostarAmount(Integer nanostarAmount) {
        this.nanostarAmount = nanostarAmount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AffiliateInfo{");
        sb.append("affiliateUser=").append(affiliateUser);
        sb.append(", affiliateChat=").append(affiliateChat);
        sb.append(", commissionPerMille=").append(commissionPerMille);
        sb.append(", amount=").append(amount);
        sb.append(", nanostarAmount=").append(nanostarAmount);
        sb.append('}');
        return sb.toString();
    }
}
