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
package org.apache.camel.component.telegram.util;

import org.apache.camel.Exchange;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.Update;

public final class TelegramMessageHelper {

    private TelegramMessageHelper() {
    }


    public static void populateExchange(Exchange exchange, Update update) {
        if (update.getMessage() != null) {
            exchange.getMessage().setBody(update.getMessage());

            if (update.getMessage().getChat() != null) {
                exchange.getMessage().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, update.getMessage().getChat().getId());
            }
        } else if (update.getChannelPost() != null) {
            exchange.getMessage().setBody(update.getChannelPost());

            if (update.getChannelPost().getChat() != null) {
                exchange.getMessage().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, update.getChannelPost().getChat().getId());
            }
        } else if (update.getCallbackQuery() != null) {
            exchange.getMessage().setBody(update.getCallbackQuery());
        } else if (update.getIncomingInlineQuery() != null) {
            exchange.getMessage().setBody(update.getIncomingInlineQuery());
        }

    }

}
