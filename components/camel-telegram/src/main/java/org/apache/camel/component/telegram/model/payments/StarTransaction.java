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
 * Describes a Telegram Star transaction.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#startransaction">https://core.telegram.org/bots/api#startransaction</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StarTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = -2340108149132641728L;

    /**
     * Unique identifier of the transaction. Coincides with the identifier of the original transaction for refund
     * transactions.
     */
    private String id;

    /**
     * Integer amount of Telegram Stars transferred by the transaction.
     */
    private Integer amount;

    /**
     * The number of 1/1000000000 shares of Telegram Stars transferred; from 0 to 999999999.
     */
    @JsonProperty("nanostar_amount")
    private Integer nanostarAmount;

    /**
     * Date the transaction was created in Unix time.
     */
    private Integer date;

    /**
     * Source of an incoming transaction. Only for incoming transactions.
     */
    private TransactionPartner source;

    /**
     * Receiver of an outgoing transaction. Only for outgoing transactions.
     */
    private TransactionPartner receiver;

    public StarTransaction() {
    }

    public StarTransaction(String id, Integer amount, Integer nanostarAmount, Integer date, TransactionPartner source,
                           TransactionPartner receiver) {
        this.id = id;
        this.amount = amount;
        this.nanostarAmount = nanostarAmount;
        this.date = date;
        this.source = source;
        this.receiver = receiver;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getDate() {
        return date;
    }

    public void setDate(Integer date) {
        this.date = date;
    }

    public TransactionPartner getSource() {
        return source;
    }

    public void setSource(TransactionPartner source) {
        this.source = source;
    }

    public TransactionPartner getReceiver() {
        return receiver;
    }

    public void setReceiver(TransactionPartner receiver) {
        this.receiver = receiver;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StarTransaction{");
        sb.append("id='").append(id).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", nanostarAmount=").append(nanostarAmount);
        sb.append(", date=").append(date);
        sb.append(", source=").append(source);
        sb.append(", receiver=").append(receiver);
        sb.append('}');
        return sb.toString();
    }
}
