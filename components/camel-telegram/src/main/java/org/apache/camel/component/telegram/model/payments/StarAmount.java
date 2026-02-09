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
 * This object describes an amount of Telegram Stars.
 *
 * @see <a href="https://core.telegram.org/bots/api#staramount">https://core.telegram.org/bots/api#staramount</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StarAmount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1273845589598186890L;

    private Integer amount;

    @JsonProperty("nanostar_amount")
    private Integer nanostarAmount;

    public StarAmount(Integer amount, Integer nanostarAmount) {
        this.amount = amount;
        this.nanostarAmount = nanostarAmount;
    }

    public StarAmount() {
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
        final StringBuilder sb = new StringBuilder("StarAmount{");
        sb.append("amount=").append(amount);
        sb.append(", nanostarAmount=").append(nanostarAmount);
        sb.append('}');
        return sb.toString();
    }
}
