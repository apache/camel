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
package org.apache.camel.dataformat.xsdjson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

abstract class XsdJsonTestSupport {

    /**
     * A thread stack that cannot hold 512 levels of nesting.
     */
    protected static final long SMALL_STACK = 128 * 1024;

    /**
     * A thread stack that holds 512 levels of nesting in any JVM mode.
     */
    protected static final long BIG_STACK = 16 * 1024 * 1024;

    protected CamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    protected XsdJsonDataFormat dataFormat(String schema, Consumer<XsdJsonDataFormat> config) {
        XsdJsonDataFormat df = new XsdJsonDataFormat();
        df.setCamelContext(context);
        df.setSchema(schema);
        config.accept(df);
        df.start();
        return df;
    }

    protected Exchange exchange() {
        return new DefaultExchange(context);
    }

    protected String toJson(XsdJsonDataFormat df, String xml) throws Exception {
        return toJson(df, exchange(), xml);
    }

    protected String toJson(XsdJsonDataFormat df, Exchange exchange, String xml) throws Exception {
        return (String) df.unmarshal(exchange, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    protected String toXml(XsdJsonDataFormat df, Object json) throws Exception {
        return toXml(df, exchange(), json);
    }

    protected String toXml(XsdJsonDataFormat df, Exchange exchange, Object json) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        df.marshal(exchange, json, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * The JSON of the element tree of shop.xsd with the given number of levels (the tree element and the node elements
     * nested in it).
     */
    protected static Map<String, Object> nestedTree(int levels) {
        Map<String, Object> tree = new LinkedHashMap<>();
        Map<String, Object> current = tree;
        for (int i = 1; i < levels; i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            current.put("node", List.of(node));
            current = node;
        }
        return tree;
    }

    /**
     * The XML of the element tree of shop.xsd with the given number of levels.
     */
    protected static String nestedXml(int levels) {
        return "<tree xmlns=\"urn:shop\">" + "<node>".repeat(levels - 1) + "</node>".repeat(levels - 1) + "</tree>";
    }

    /**
     * Runs the task on a new thread with the given stack size, and returns what it threw, or null.
     */
    protected static Throwable thrownOnThread(long stackSize, Executable task) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                task.execute();
            } catch (Throwable e) {
                thrown.set(e);
            }
        }, "stack-" + stackSize, stackSize);
        thread.start();
        thread.join();
        return thrown.get();
    }
}
