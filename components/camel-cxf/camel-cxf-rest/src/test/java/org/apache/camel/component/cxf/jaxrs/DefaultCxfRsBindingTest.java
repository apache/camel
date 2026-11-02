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
package org.apache.camel.component.cxf.jaxrs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultCxfRsBindingTest {

    private DefaultCamelContext context = new DefaultCamelContext();

    @Test
    public void testSetCharsetWithContentType() {
        DefaultCxfRsBinding cxfRsBinding = new DefaultCxfRsBinding();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml;charset=ISO-8859-1");
        cxfRsBinding.setCharsetWithContentType(exchange);

        String charset = ExchangeHelper.getCharsetName(exchange);
        assertEquals("ISO-8859-1", charset, "Get a wrong charset");

        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        cxfRsBinding.setCharsetWithContentType(exchange);
        charset = ExchangeHelper.getCharsetName(exchange);
        assertEquals("UTF-8", charset, "Get a worng charset name");
    }

    @Test
    public void testContentLanguagePerRequest() throws Exception {
        DefaultCxfRsBinding binding = new DefaultCxfRsBinding();
        Exchange exchange = new DefaultExchange(context);
        Message camelMessage = exchange.getIn();
        camelMessage.setBody("test");

        WebClient frClient = WebClient.create("http://localhost");
        frClient.header(HttpHeaders.CONTENT_LANGUAGE, "fr");
        Entity<Object> frEntity = binding.bindCamelMessageToRequestEntity("body", camelMessage, exchange, frClient);
        assertEquals(Locale.FRENCH, frEntity.getVariant().getLanguage());

        WebClient deClient = WebClient.create("http://localhost");
        deClient.header(HttpHeaders.CONTENT_LANGUAGE, "de");
        Entity<Object> deEntity = binding.bindCamelMessageToRequestEntity("body", camelMessage, exchange, deClient);
        assertEquals(Locale.GERMAN, deEntity.getVariant().getLanguage());
    }

    @Test
    public void testContentLanguageDefaultsToUS() throws Exception {
        DefaultCxfRsBinding binding = new DefaultCxfRsBinding();
        Exchange exchange = new DefaultExchange(context);
        Message camelMessage = exchange.getIn();

        Entity<Object> entity = binding.bindCamelMessageToRequestEntity("body", camelMessage, exchange, null);
        assertEquals(Locale.US, entity.getVariant().getLanguage());
    }

    @Test
    public void testCopyProtocolHeader() {
        DefaultCxfRsBinding cxfRsBinding = new DefaultCxfRsBinding();
        cxfRsBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        Message camelMessage = new DefaultMessage(context);
        org.apache.cxf.message.Message cxfMessage = new MessageImpl();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("emptyList", Collections.<String> emptyList());
        headers.put("zeroSizeList", new ArrayList<String>(0));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);
        cxfRsBinding.copyProtocolHeader(cxfMessage, camelMessage, exchange);
        assertNull(camelMessage.getHeader("emptyList"), "We should get nothing here");
        assertNull(camelMessage.getHeader("zeroSizeList"), "We should get nothing here");
    }

    @Test
    public void testResponseUnwrapsSingleElementMessageContentsList() throws Exception {
        DefaultCxfRsBinding binding = new DefaultCxfRsBinding();

        assertEquals("Mary", binding.populateCxfRsResponseFromExchange(
                replyExchange(new MessageContentsList("Mary")), new ExchangeImpl()));
        // no content when the resource method has no parameter
        assertNull(binding.populateCxfRsResponseFromExchange(
                replyExchange(new MessageContentsList()), new ExchangeImpl()));
        // several parameters are left as is
        MessageContentsList several = new MessageContentsList("123", "Mary");
        assertSame(several, binding.populateCxfRsResponseFromExchange(replyExchange(several), new ExchangeImpl()));
        // a List the route sets as reply is the entity
        List<String> list = List.of("Mary");
        assertSame(list, binding.populateCxfRsResponseFromExchange(replyExchange(list), new ExchangeImpl()));
    }

    @Test
    public void testSimpleBindingResponseHasNoEntityForMessageContentsList() throws Exception {
        SimpleCxfRsBinding binding = new SimpleCxfRsBinding();
        binding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());

        Object answer = binding.populateCxfRsResponseFromExchange(
                replyExchange(new MessageContentsList("123")), new ExchangeImpl());
        Response response = assertInstanceOf(Response.class, answer);
        assertFalse(response.hasEntity());
    }

    @Test
    public void testRequestBodyOfList() throws Exception {
        DefaultCxfRsBinding binding = new DefaultCxfRsBinding();
        Exchange exchange = new DefaultExchange(context);

        // all the elements of a List are sent
        List<String> list = List.of("a", "b", "c");
        exchange.getIn().setBody(list);
        assertSame(list, binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        List<String> single = List.of("a");
        exchange.getIn().setBody(single);
        assertSame(single, binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        // the entity of a MessageContentsList is its first element
        exchange.getIn().setBody(new MessageContentsList("a", "b"));
        assertEquals("a", binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        exchange.getIn().setBody(new MessageContentsList());
        assertNull(binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));
    }

    @Test
    public void testRequestBodyOfArray() throws Exception {
        DefaultCxfRsBinding binding = new DefaultCxfRsBinding();
        Exchange exchange = new DefaultExchange(context);

        // a primitive array is the entity as a whole
        int[] ints = { 1, 2, 3 };
        exchange.getIn().setBody(ints);
        assertSame(ints, binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        long[] longs = { 1L, 2L };
        exchange.getIn().setBody(longs);
        assertSame(longs, binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        byte[] bytes = { 1, 2 };
        exchange.getIn().setBody(bytes);
        assertSame(bytes, binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        // the entity of an Object[] is its first element, as of a MessageContentsList
        exchange.getIn().setBody(new Object[] { "a", "b" });
        assertEquals("a", binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));

        exchange.getIn().setBody(new String[] { "a", "b" });
        assertEquals("a", binding.bindCamelMessageBodyToRequestBody(exchange.getIn(), exchange));
    }

    private Exchange replyExchange(Object body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        return exchange;
    }

}
