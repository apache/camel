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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * This object describes the source of a transaction, or its recipient for outgoing transactions.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartner">https://core.telegram.org/bots/api#transactionpartner</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionPartnerUser.class, name = "user"),
        @JsonSubTypes.Type(value = TransactionPartnerChat.class, name = "chat"),
        @JsonSubTypes.Type(value = TransactionPartnerAffiliateProgram.class, name = "affiliate_program"),
        @JsonSubTypes.Type(value = TransactionPartnerFragment.class, name = "fragment"),
        @JsonSubTypes.Type(value = TransactionPartnerTelegramAds.class, name = "telegram_ads"),
        @JsonSubTypes.Type(value = TransactionPartnerTelegramApi.class, name = "telegram_api"),
        @JsonSubTypes.Type(value = TransactionPartnerOther.class, name = "other")
})
public abstract class TransactionPartner implements Serializable {

    @Serial
    private static final long serialVersionUID = -977981121195306104L;

    /**
     * Type of the transaction partner.
     */
    private String type;

    public TransactionPartner() {
    }

    public TransactionPartner(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns this object as {@link TransactionPartnerUser} if it is of that type, null otherwise.
     */
    public TransactionPartnerUser asUser() {
        return this instanceof TransactionPartnerUser ? (TransactionPartnerUser) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerChat} if it is of that type, null otherwise.
     */
    public TransactionPartnerChat asChat() {
        return this instanceof TransactionPartnerChat ? (TransactionPartnerChat) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerAffiliateProgram} if it is of that type, null otherwise.
     */
    public TransactionPartnerAffiliateProgram asAffiliateProgram() {
        return this instanceof TransactionPartnerAffiliateProgram ? (TransactionPartnerAffiliateProgram) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerFragment} if it is of that type, null otherwise.
     */
    public TransactionPartnerFragment asFragment() {
        return this instanceof TransactionPartnerFragment ? (TransactionPartnerFragment) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerTelegramAds} if it is of that type, null otherwise.
     */
    public TransactionPartnerTelegramAds asTelegramAds() {
        return this instanceof TransactionPartnerTelegramAds ? (TransactionPartnerTelegramAds) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerTelegramApi} if it is of that type, null otherwise.
     */
    public TransactionPartnerTelegramApi asTelegramApi() {
        return this instanceof TransactionPartnerTelegramApi ? (TransactionPartnerTelegramApi) this : null;
    }

    /**
     * Returns this object as {@link TransactionPartnerOther} if it is of that type, null otherwise.
     */
    public TransactionPartnerOther asOther() {
        return this instanceof TransactionPartnerOther ? (TransactionPartnerOther) this : null;
    }
}
