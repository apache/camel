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
package org.apache.camel.component.langchain4j.chat;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.rag.content.Content;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.chat.rag.LangChain4jRagAggregatorStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChat.Headers.AUGMENTED_DATA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LangChain4jRagAggregatorTest {

    private LangChain4jRagAggregatorStrategy aggregator;
    private Exchange oldExchange;
    private Exchange newExchange;

    @BeforeEach
    void setUp() {
        aggregator = new LangChain4jRagAggregatorStrategy();
        oldExchange = new DefaultExchange(new DefaultCamelContext());
        newExchange = new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    void testAggregateWithNoNewData() {
        Exchange result = aggregator.aggregate(oldExchange, newExchange);
        assertEquals(oldExchange, result);
    }

    @Test
    void testAggregateWithNewData() {

        // setting a prompt in the old Exchange
        oldExchange.getIn().setBody("Prompt Test");

        // setting augmented data in the new Exchange
        List<String> newData = List.of("data1", "data2");
        newExchange.getIn().setBody(newData);

        Exchange result = aggregator.aggregate(oldExchange, newExchange);

        List<Content> contents = result.getIn().getHeader(AUGMENTED_DATA, List.class);
        String prompt = result.getIn().getBody(String.class);

        assertNotNull("The body should contain the old body", prompt);
        assertEquals("Prompt Test", prompt);

        assertNotNull("The old exchange should contain now the enriched data in type of List of Content", contents);
        assertEquals(2, contents.size());

        assertTrue("The first content item should match one of the new data entries.",
                newData.contains(contents.get(0).textSegment().text()));
        assertTrue("The second content item should match one of the new data entries.",
                newData.contains(contents.get(1).textSegment().text()));
    }

    @Test
    void testAggregateWithExistingAndNewData() {

        // setting a prompt in the old Exchange
        oldExchange.getIn().setBody("Prompt Test");

        // setting a content in the old exchange
        Content oldContent = new Content("Old data");
        List<Content> contents = new ArrayList<>();
        contents.add(oldContent);
        oldExchange.getIn().setHeader(AUGMENTED_DATA, contents);

        // setting augmented data in the new Exchange
        List<String> newData = List.of("data1", "data2");
        newExchange.getIn().setBody(newData);

        Exchange result = aggregator.aggregate(oldExchange, newExchange);

        contents = result.getIn().getHeader(AUGMENTED_DATA, List.class);
        String prompt = result.getIn().getBody(String.class);

        assertNotNull("The body should contain the old body", prompt);
        assertEquals("Prompt Test", prompt);

        assertNotNull("The old exchange should contain now the enriched data in type of List of Content", contents);
        assertEquals(3, contents.size());

        assertEquals("The first content item should match the old content", "Old data", contents.get(0).textSegment().text());
        assertTrue("The second content item should match one of the new data entries.",
                newData.contains(contents.get(1).textSegment().text()));
        assertTrue("The third content item should match one of the new data entries.",
                newData.contains(contents.get(2).textSegment().text()));
    }

    @Test
    void testOldExchangeIsNull() {
        newExchange.getMessage().setHeader(AUGMENTED_DATA, "Additional data");
        Exchange result = aggregator.aggregate(null, newExchange);
        assertEquals(newExchange, result);
    }
}
