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
import org.apache.camel.component.telegram.TelegramMessage;

/**
 * Use this method to get the list of bot's transactions in Telegram Stars in chronological order. On success, returns a
 * StarTransactions object.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#getstartransactions">https://core.telegram.org/bots/api#getstartransactions</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetStarTransactionsMessage implements TelegramMessage {

    @Serial
    private static final long serialVersionUID = -2586213653160802050L;

    /**
     * Number of transactions to skip in the response.
     */
    private Integer offset;

    /**
     * The maximum number of transactions to be retrieved. Values between 1-100 are accepted. Defaults to 100.
     */
    private Integer limit;

    public GetStarTransactionsMessage() {
    }

    public GetStarTransactionsMessage(Integer offset, Integer limit) {
        this.offset = offset;
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GetStarTransactionsMessage{");
        sb.append("offset=").append(offset);
        sb.append(", limit=").append(limit);
        sb.append('}');
        return sb.toString();
    }
}
