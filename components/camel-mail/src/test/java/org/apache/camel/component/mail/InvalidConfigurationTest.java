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
package org.apache.camel.component.mail;

import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for various invalid configurations etc.
 */
public class InvalidConfigurationTest extends CamelTestSupport {

    @Test
    public void testSMTPCanNotBeUsedForConsumingMails() throws Exception {
        Endpoint endpoint = context.getEndpoint("smtp://localhost?username=james");
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            consumer.start();
            fail("Should have thrown NoSuchProviderException as smtp protocol cannot be used for consuming mails");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSMTPSCanNotBeUsedForConsumingMails() throws Exception {
        Endpoint endpoint = context.getEndpoint("smtps://localhost?username=james");
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            consumer.start();
            fail("Should have thrown NoSuchProviderException as smtp protocol cannot be used for consuming mails");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

}
