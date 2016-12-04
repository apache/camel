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

import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Test;

/**
 * Tests the usage of defaults in the component configuration
 */
public class TelegramComponentParametersTest extends TelegramTestSupport {

    @Test
    public void testDefaultsAndOverrides() throws Exception {

        TelegramComponent component = (TelegramComponent) context().getComponent("telegram");

        component.setAuthorizationToken("DEFAULT");

        TelegramEndpoint ep1 = (TelegramEndpoint) component.createEndpoint("bots");
        assertEquals("DEFAULT", ep1.getConfiguration().getAuthorizationToken());

        TelegramEndpoint ep2 = (TelegramEndpoint) component.createEndpoint("bots/CUSTOM");
        assertEquals("CUSTOM", ep2.getConfiguration().getAuthorizationToken());

        TelegramEndpoint ep3 = (TelegramEndpoint) component.createEndpoint("bots/ANOTHER?chatId=123");
        assertEquals("ANOTHER", ep3.getConfiguration().getAuthorizationToken());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonDefaultConfig() throws Exception {
        TelegramComponent component = (TelegramComponent) context().getComponent("telegram");
        component.setAuthorizationToken(null);
        component.createEndpoint("bots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongURI1() throws Exception {
        TelegramComponent component = (TelegramComponent) context().getComponent("telegram");
        component.setAuthorizationToken("ANY");
        component.createEndpoint("bots/ ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongURI2() throws Exception {
        TelegramComponent component = (TelegramComponent) context().getComponent("telegram");
        component.setAuthorizationToken("ANY");
        component.createEndpoint("bots/token/s");
    }

}
