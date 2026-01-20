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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.model.User;

/**
 * Describes the affiliate program that issued the affiliate commission received via this transaction.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartneraffiliateprogram">https://core.telegram.org/bots/api#transactionpartneraffiliateprogram</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPartnerAffiliateProgram extends TransactionPartner {

    @Serial
    private static final long serialVersionUID = -94537940085654083L;

    /**
     * The bot that sponsored the affiliate program.
     */
    @JsonProperty("sponsor_user")
    private User sponsorUser;

    /**
     * The number of Telegram Stars received by the bot for each 1000 Telegram Stars received by the affiliate program
     * sponsor from referred users.
     */
    @JsonProperty("commission_per_mille")
    private Integer commissionPerMille;

    public TransactionPartnerAffiliateProgram() {
        super("affiliate_program");
    }

    public User getSponsorUser() {
        return sponsorUser;
    }

    public void setSponsorUser(User sponsorUser) {
        this.sponsorUser = sponsorUser;
    }

    public Integer getCommissionPerMille() {
        return commissionPerMille;
    }

    public void setCommissionPerMille(Integer commissionPerMille) {
        this.commissionPerMille = commissionPerMille;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionPartnerAffiliateProgram{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", sponsorUser=").append(sponsorUser);
        sb.append(", commissionPerMille=").append(commissionPerMille);
        sb.append('}');
        return sb.toString();
    }
}
