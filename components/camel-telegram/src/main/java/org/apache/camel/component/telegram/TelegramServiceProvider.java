/**
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
package org.apache.camel.component.telegram;

import org.apache.camel.component.telegram.service.TelegramServiceRestBotAPIAdapter;

/**
 * Provides access to an instance of the Telegram service. It allows changing the default implementation of the service for testing purposes.
 * Currently the Telegram API does not allow a Bot sending messages to other BOTs (https://core.telegram.org/bots/faq#why-doesn-39t-my-bot-see-messages-from-other-bots),
 * so the service needs to be mocked for end-to-end testing.
 *
 * The Rest client used as default implementation is thread safe, considering the current usage of the APIs. It is treated as a singleton.
 */
public final class TelegramServiceProvider {

    private static final TelegramServiceProvider INSTANCE = new TelegramServiceProvider();

    /**
     * The default service.
     */
    private final TelegramService service;

    /**
     * An alternative service used for testing purposes.
     */
    private TelegramService telegramService;

    private TelegramServiceProvider() {
        // Using the Rest Bot API by default
        this.service = new TelegramServiceRestBotAPIAdapter();
    }

    /**
     * Returns the singleton provider.
     */
    public static TelegramServiceProvider get() {
        return INSTANCE;
    }

    /**
     * Provides the current service. It can be the default one or an alternative one.
     * @return the active {@code TelegramService}
     */
    public TelegramService getService() {
        if (telegramService != null) {
            // no need for synchronization, it's only for testing purposes
            return telegramService;
        }
        return service;
    }

    /**
     * Get the current alternative service, if any.
     *
     * @return the current alternative service
     */
    public TelegramService getAlternativeService() {
        return telegramService;
    }

    /**
     * Allows setting an alternative service.
     *
     * @param service the alternative service
     */
    public void setAlternativeService(TelegramService service) {
        this.telegramService = service;
    }

    /**
     * Restores the provider to its original state.
     */
    public void restoreDefaultService() {
        this.telegramService = null;
    }

}
