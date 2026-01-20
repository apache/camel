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
 * Use this method to get the current bot's Telegram Star balance. On success, returns a StarAmount object.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#getmystarbalance">https://core.telegram.org/bots/api#getmystarbalance</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetMyStarBalanceMessage implements TelegramMessage {

    @Serial
    private static final long serialVersionUID = 5869515904295554780L;

    public GetMyStarBalanceMessage() {
    }

    @Override
    public String toString() {
        return "GetMyStarBalanceMessage{}";
    }
}
