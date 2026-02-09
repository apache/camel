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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contains a list of Telegram Star transactions.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#startransactions">https://core.telegram.org/bots/api#startransactions</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StarTransactions implements Serializable {

    @Serial
    private static final long serialVersionUID = -2967282452206464640L;

    /**
     * The list of transactions.
     */
    private List<StarTransaction> transactions;

    public StarTransactions() {
    }

    public StarTransactions(List<StarTransaction> transactions) {
        this.transactions = transactions;
    }

    public List<StarTransaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<StarTransaction> transactions) {
        this.transactions = transactions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StarTransactions{");
        sb.append("transactions=").append(transactions);
        sb.append('}');
        return sb.toString();
    }
}
