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
package org.apache.camel.builder.xml;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltBuilderReloadTest extends ContextTestSupport {

    private static final String INPUT = "<hello>world!</hello>";
    private static final String EXPECTED_V1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>";
    private static final String EXPECTED_V2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><farewell>world!</farewell>";

    @Test
    public void testPooledTransformersInvalidatedOnTemplateChange() throws Exception {
        URL styleSheet1 = getClass().getResource("example.xsl");
        URL styleSheet2 = getClass().getResource("example2.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet1);
        builder.transformerCacheSize(10);

        // process first message — transformer compiled from stylesheet 1 is returned to pool
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody(INPUT);
        builder.process(exchange1);
        assertEquals(EXPECTED_V1, exchange1.getMessage().getBody(String.class));

        // reload stylesheet — pool must be invalidated
        builder.setTransformerSource(new StreamSource(styleSheet2.openStream()));

        // process second message — must use new template, not a stale pooled transformer
        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody(INPUT);
        builder.process(exchange2);
        assertEquals(EXPECTED_V2, exchange2.getMessage().getBody(String.class));
    }

    @Test
    public void testMultipleReloadsWithTransformerCache() throws Exception {
        URL styleSheet1 = getClass().getResource("example.xsl");
        URL styleSheet2 = getClass().getResource("example2.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet1);
        builder.transformerCacheSize(5);

        // fill the pool with a few transformers from stylesheet 1
        for (int i = 0; i < 3; i++) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(INPUT);
            builder.process(exchange);
            assertEquals(EXPECTED_V1, exchange.getMessage().getBody(String.class));
        }

        // reload to stylesheet 2
        builder.setTransformerSource(new StreamSource(styleSheet2.openStream()));

        // all subsequent messages must use the new stylesheet
        for (int i = 0; i < 5; i++) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(INPUT);
            builder.process(exchange);
            assertEquals(EXPECTED_V2, exchange.getMessage().getBody(String.class),
                    "Message " + i + " should use the new stylesheet");
        }

        // reload back to stylesheet 1
        builder.setTransformerSource(new StreamSource(styleSheet1.openStream()));

        for (int i = 0; i < 3; i++) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(INPUT);
            builder.process(exchange);
            assertEquals(EXPECTED_V1, exchange.getMessage().getBody(String.class));
        }
    }

    @Test
    public void testConcurrentProcessingDuringReload() throws Exception {
        URL styleSheet1 = getClass().getResource("example.xsl");
        URL styleSheet2 = getClass().getResource("example2.xsl");

        XsltBuilder builder = XsltBuilder.xslt(styleSheet1);
        builder.transformerCacheSize(10);

        // warm up the pool
        Exchange warmup = new DefaultExchange(context);
        warmup.getIn().setBody(INPUT);
        builder.process(warmup);

        // reload to stylesheet 2
        builder.setTransformerSource(new StreamSource(styleSheet2.openStream()));

        // launch multiple threads that all process concurrently after the reload
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await(10, TimeUnit.SECONDS);
                    Exchange exchange = new DefaultExchange(context);
                    exchange.getIn().setBody(INPUT);
                    builder.process(exchange);
                    synchronized (results) {
                        results.add(exchange.getMessage().getBody(String.class));
                    }
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        // release all threads at once
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete");
        assertTrue(errors.isEmpty(), "No errors expected: " + errors);
        assertEquals(threadCount, results.size());

        // all results must use the new stylesheet
        for (int i = 0; i < results.size(); i++) {
            assertEquals(EXPECTED_V2, results.get(i),
                    "Thread " + i + " should use the new stylesheet");
        }
    }
}
