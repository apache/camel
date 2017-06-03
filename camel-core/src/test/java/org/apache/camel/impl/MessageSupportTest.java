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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;

/**
 * @version 
 */
public class MessageSupportTest extends ContextTestSupport {

    public void testSetBodyType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = exchange.getIn();
        in.setBody("123", Integer.class);

        assertIsInstanceOf(Integer.class, in.getBody());
    }

    public void testGetMandatoryBody() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = exchange.getIn();

        try {
            in.getMandatoryBody();
            fail("Should have thrown an exception");
        } catch (InvalidPayloadException e) {
            // expected
        }

        in.setBody("Hello World");

        assertEquals("Hello World", in.getMandatoryBody());
    }

    public void testGetMessageId() {
        context.setUuidGenerator(new SimpleUuidGenerator());
        Exchange exchange = new DefaultExchange(context);
        Message in = exchange.getIn();
        
        assertEquals("1", in.getMessageId());
    }
    
    public void testGetMessageIdWithoutAnExchange() {
        Message in = new DefaultMessage(context);
        
        assertNotNull(in.getMessageId());
    }

    public void testCopyFromSameHeadersInstance() {
        Exchange exchange = new DefaultExchange(context);

        Message in = exchange.getIn();
        Map<String, Object> headers = in.getHeaders();
        headers.put("foo", 123);

        Message out = new DefaultMessage(context);
        out.setBody("Bye World");
        out.setHeaders(headers);

        out.copyFrom(in);

        assertEquals(123, headers.get("foo"));
        assertEquals(123, in.getHeader("foo"));
        assertEquals(123, out.getHeader("foo"));
    }
}