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
package org.apache.camel.component.telegram.integration;

import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.util.TelegramApiConfig;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "TELEGRAM_AUTHORIZATION_TOKEN", matches = ".*")
public class TelegramServiceProxyIT extends TelegramTestSupport {

    private static String proxyHost;
    private static String proxyPort;
    private static String proxyType;

    @BeforeAll
    public static void configureProxyFromEnv() {
        proxyHost = System.getenv("TELEGRAM_PROXY_HOST");
        Assumptions.assumeTrue(proxyHost != null, "There is no proxy host defined on this environment");

        proxyPort = System.getenv("TELEGRAM_PROXY_PORT");
        Assumptions.assumeTrue(proxyPort != null, "There is no proxy port defined on this environment");

        proxyType = System.getenv("TELEGRAM_PROXY_TYPE");
        Assumptions.assumeTrue(proxyType != null, "There is no proxy type defined on this environment");
    }

    protected TelegramApiConfig getTelegramApiConfig() {
        return TelegramApiConfig.fromEnv();
    }

    @Test
    public void testGetUpdates() {
        IncomingMessage res = consumer.receiveBody(
                String.format("telegram://bots?proxyHost=%s&proxyPort=%s&proxyType=%s", proxyHost, proxyPort, proxyType), 5000,
                IncomingMessage.class);
        assertNotNull(res);
    }

    @Test
    public void testSendMessage() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an auto-generated message from the Bot");
        Assertions.assertDoesNotThrow(() -> template.requestBody(
                String.format("telegram://bots?chatId=%s&proxyHost=%s&proxyPort=%s&proxyType=%s", chatId, proxyHost, proxyPort,
                        proxyType),
                msg));
    }

}
