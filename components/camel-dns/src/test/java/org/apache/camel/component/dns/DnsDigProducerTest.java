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
package org.apache.camel.component.dns;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the query that the dig producer builds from the headers, without sending it.
 */
public class DnsDigProducerTest {

    private CamelContext context;
    private DnsDigProducer producer;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        producer = new DnsDigProducer(context.getEndpoint("dns:dig"));
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void typeAndClassDefaultToAAndIn() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DnsConstants.DNS_NAME, "camel.apache.org");

        Record question = producer.createQuery(exchange).getQuestion();

        assertEquals("camel.apache.org.", question.getName().toString());
        assertEquals(Type.A, question.getType());
        assertEquals(DClass.IN, question.getDClass());
    }

    @Test
    public void typeAndClassFromHeaders() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DnsConstants.DNS_NAME, "camel.apache.org");
        exchange.getIn().setHeader(DnsConstants.DNS_TYPE, "MX");
        exchange.getIn().setHeader(DnsConstants.DNS_CLASS, "CH");

        Message query = producer.createQuery(exchange);

        assertEquals(Type.MX, query.getQuestion().getType());
        assertEquals(DClass.CH, query.getQuestion().getDClass());
    }

    @Test
    public void missingNameFails() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DnsConstants.DNS_TYPE, "A");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> producer.createQuery(exchange));
        assertTrue(e.getMessage().contains(DnsConstants.DNS_NAME), e.getMessage());
    }
}
