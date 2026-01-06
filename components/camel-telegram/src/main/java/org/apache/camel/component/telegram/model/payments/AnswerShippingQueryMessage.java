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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.TelegramMessage;

/**
 * If you sent an invoice requesting a shipping address and the parameter is_flexible was specified, the Bot API will
 * send an Update with a shipping_query field to the bot. Use this method to reply to shipping queries. On success, True
 * is returned.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#answershippingquery">https://core.telegram.org/bots/api#answershippingquery</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerShippingQueryMessage implements TelegramMessage {

    @Serial
    private static final long serialVersionUID = 2895708820216636657L;

    @JsonProperty("shipping_query_id")
    private String shippingQueryId;

    private Boolean ok;

    @JsonProperty("shipping_options")
    private List<ShippingOption> shippingOptions;

    @JsonProperty("error_message")
    private String errorMessage;

    public AnswerShippingQueryMessage(String shippingQueryId, Boolean ok, List<ShippingOption> shippingOptions,
                                      String errorMessage) {
        this.shippingQueryId = shippingQueryId;
        this.ok = ok;
        this.shippingOptions = shippingOptions;
        this.errorMessage = errorMessage;
    }

    public AnswerShippingQueryMessage() {
    }

    public String getShippingQueryId() {
        return shippingQueryId;
    }

    public void setShippingQueryId(String shippingQueryId) {
        this.shippingQueryId = shippingQueryId;
    }

    public Boolean getOk() {
        return ok;
    }

    public void setOk(Boolean ok) {
        this.ok = ok;
    }

    public List<ShippingOption> getShippingOptions() {
        return shippingOptions;
    }

    public void setShippingOptions(List<ShippingOption> shippingOptions) {
        this.shippingOptions = shippingOptions;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AnswerShippingQueryMessage{");
        sb.append("shippingQueryId='").append(shippingQueryId).append('\'');
        sb.append(", ok=").append(ok);
        sb.append(", shippingOptions=").append(shippingOptions);
        sb.append(", errorMessage='").append(errorMessage).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
