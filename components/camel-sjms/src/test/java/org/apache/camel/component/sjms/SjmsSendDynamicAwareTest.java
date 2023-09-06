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
package org.apache.camel.component.sjms;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SjmsSendDynamicAwareTest extends CamelTestSupport {

    SjmsSendDynamicAware sjmsSendDynamicAware;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.sjmsSendDynamicAware = new SjmsSendDynamicAware();
    }

    @Test
    public void testUriParsing() throws Exception {
        this.sjmsSendDynamicAware.setScheme("sjms");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry
                = new SendDynamicAware.DynamicAwareEntry(
                        "sjms:destination.SjmsSendDynamicAwareTest", "sjms:${header.test}", null, null);
        Processor processor = this.sjmsSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination.SjmsSendDynamicAwareTest",
                exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME));
    }

    @Test
    public void testSlashedUriParsing() throws Exception {
        this.sjmsSendDynamicAware.setScheme("sjms");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry
                = new SendDynamicAware.DynamicAwareEntry(
                        "sjms://destination.SjmsSendDynamicAwareTest", "sjms://${header.test}", null, null);
        Processor processor = this.sjmsSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination.SjmsSendDynamicAwareTest",
                exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME));
    }
}
