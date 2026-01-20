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
 * Describes a transaction with payment for paid broadcasting.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartnertelegramapi">https://core.telegram.org/bots/api#transactionpartnertelegramapi</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPartnerTelegramApi extends TransactionPartner {

    @Serial
    private static final long serialVersionUID = 161396113287446225L;

    /**
     * The number of successful requests that exceeded regular limits and were therefore billed.
     */
    @JsonProperty("request_count")
    private Integer requestCount;

    public TransactionPartnerTelegramApi() {
        super("telegram_api");
    }

    public Integer getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Integer requestCount) {
        this.requestCount = requestCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionPartnerTelegramApi{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", requestCount=").append(requestCount);
        sb.append('}');
        return sb.toString();
    }
}
