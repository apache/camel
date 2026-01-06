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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This object represents one shipping option.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#shippingoption">https://core.telegram.org/bots/api#shippingoption</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShippingOption implements Serializable {

    private static final long serialVersionUID = 1434858887897371221L;

    private String id;
    private String title;
    private List<LabeledPrice> prices;

    public ShippingOption(String id, String title, List<LabeledPrice> prices) {
        this.id = id;
        this.title = title;
        this.prices = prices;
    }

    public ShippingOption() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<LabeledPrice> getPrices() {
        return prices;
    }

    public void setPrices(List<LabeledPrice> prices) {
        this.prices = prices;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ShippingOption{");
        sb.append("id='").append(id).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", prices=").append(prices);
        sb.append('}');
        return sb.toString();
    }
}
