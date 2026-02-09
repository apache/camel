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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This object represents a portion of the price for goods or services.
 *
 * @see <a href="https://core.telegram.org/bots/api#labeledprice">https://core.telegram.org/bots/api#labeledprice</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LabeledPrice implements Serializable {

    private static final long serialVersionUID = 266995423954925422L;

    private String label;

    private Integer amount;

    public LabeledPrice(String label, Integer amount) {
        this.label = label;
        this.amount = amount;
    }

    public LabeledPrice() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LabeledPrice{");
        sb.append("label='").append(label).append('\'');
        sb.append(", amount=").append(amount);
        sb.append('}');
        return sb.toString();
    }
}
