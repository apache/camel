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
package org.apache.camel.component.telegram.integration;

import org.apache.camel.component.telegram.TelegramService;
import org.apache.camel.component.telegram.TelegramServiceProvider;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.cxf.transports.http.configuration.ProxyServerType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests if the BotAPI are working correctly over proxy.
 */
public class TelegramServiceProxyTest {

    private static String authorizationToken;

    private static String chatId;

    private static String proxyHost;

    private static int proxyPort;

    private static ProxyServerType proxyType;

    @BeforeClass
    public static void init() {
        authorizationToken = System.getenv("TELEGRAM_AUTHORIZATION_TOKEN");
        chatId = System.getenv("TELEGRAM_CHAT_ID");
        proxyHost = System.getenv("TELEGRAM_PROXY_HOST");
        proxyPort = Integer.parseInt(System.getenv("TELEGRAM_PROXY_PORT"));
        proxyType = ProxyServerType.valueOf(System.getenv("TELEGRAM_PROXY_TYPE"));
    }

    @Test
    public void testGetUpdates() {
        TelegramService service = TelegramServiceProvider.get().getService();
        service.setProxy(proxyHost, proxyPort, proxyType);

        UpdateResult res = service.getUpdates(authorizationToken, null, null, null);

        Assert.assertNotNull(res);
        Assert.assertTrue(res.isOk());
    }

    @Test
    public void testSendMessage() {
        TelegramService service = TelegramServiceProvider.get().getService();
        service.setProxy(proxyHost, proxyPort, proxyType);

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an auto-generated message from the Bot");

        service.sendMessage(authorizationToken, msg);
    }
}
