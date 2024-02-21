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
package org.apache.camel.component.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsProducerDisableReplyToTest extends AbstractPersistentJMSTest {

    @Test
    public void testProducerDisableReplyTo() {
        // must start CamelContext because use route builder is false
        context.start();

        String url = "activemq:queue:JmsProducerDisableReplyToTest?disableReplyTo=true";
        template.requestBody(url, "Hello World");

        Object out = consumer.receiveBody(url, 5000);
        assertEquals("Hello World", out);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
