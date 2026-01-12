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

/**
 * Describes a withdrawal transaction with Fragment.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartnerfragment">https://core.telegram.org/bots/api#transactionpartnerfragment</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPartnerFragment extends TransactionPartner {

    @Serial
    private static final long serialVersionUID = 4301816007577685286L;

    /**
     * State of the transaction if the transaction is outgoing.
     */
    @JsonProperty("withdrawal_state")
    private RevenueWithdrawalState withdrawalState;

    public TransactionPartnerFragment() {
        super("fragment");
    }

    public RevenueWithdrawalState getWithdrawalState() {
        return withdrawalState;
    }

    public void setWithdrawalState(RevenueWithdrawalState withdrawalState) {
        this.withdrawalState = withdrawalState;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionPartnerFragment{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", withdrawalState=").append(withdrawalState);
        sb.append('}');
        return sb.toString();
    }
}
