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
package org.apache.camel.component.chatscript;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatScriptProducerTest {

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void chatUserNameIsAppliedToTheBot() {
        ChatScriptEndpoint endpoint = context.getEndpoint(
                "chatscript:localhost:1024/testbot?chatUserName=alice", ChatScriptEndpoint.class);
        assertEquals("alice", endpoint.getBot().getUserName(),
                "the configured chatUserName must be the conversation user name");
    }

    @Test
    void nonStringBodyIsRejectedWithAClearError() throws Exception {
        ChatScriptEndpoint endpoint = context.getEndpoint(
                "chatscript:localhost:1024/testbot", ChatScriptEndpoint.class);
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new byte[] { 1, 2, 3 });

        // Previously this returned null from buildMessage and NPE'd; it must now fail with a clear error.
        assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
    }
}
