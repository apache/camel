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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.TelegramMessage;

/**
 * Use this method to respond to the pre-checkout queries. Note: The Bot API must receive an answer within 10 seconds
 * after the pre-checkout query was sent.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#answerprecheckoutquery">https://core.telegram.org/bots/api#answerprecheckoutquery</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerPreCheckoutQueryMessage implements TelegramMessage {

    @Serial
    private static final long serialVersionUID = -3641680219624856747L;

    @JsonProperty("pre_checkout_query_id")
    private String preCheckoutQueryId;

    private Boolean ok;

    @JsonProperty("error_message")
    private String errorMessage;

    public AnswerPreCheckoutQueryMessage(String preCheckoutQueryId, Boolean ok, String errorMessage) {
        this.preCheckoutQueryId = preCheckoutQueryId;
        this.ok = ok;
        this.errorMessage = errorMessage;
    }

    public AnswerPreCheckoutQueryMessage() {
    }

    public String getPreCheckoutQueryId() {
        return preCheckoutQueryId;
    }

    public void setPreCheckoutQueryId(String preCheckoutQueryId) {
        this.preCheckoutQueryId = preCheckoutQueryId;
    }

    public Boolean getOk() {
        return ok;
    }

    public void setOk(Boolean ok) {
        this.ok = ok;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AnswerPreCheckoutQueryMessage{");
        sb.append("preCheckoutQueryId='").append(preCheckoutQueryId).append('\'');
        sb.append(", ok=").append(ok);
        sb.append(", errorMessage='").append(errorMessage).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
