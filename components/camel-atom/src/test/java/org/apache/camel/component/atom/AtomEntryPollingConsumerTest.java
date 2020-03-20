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
package org.apache.camel.component.atom;

import java.text.SimpleDateFormat;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Unit test for AtomEntryPollingConsumer
 */
public class AtomEntryPollingConsumerTest extends CamelTestSupport {

    @Test
    void testResult() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedMessageCount(7);
        mock.assertIsSatisfied();
    }

    @Test
    void testResult2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(7);
        mock.assertIsSatisfied();
    }

    @Test
    void testResult3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result3");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        registry.bind("myDate", df.parse("2007-11-13 14:35:00 +0100"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("atom:file:src/test/data/feed.atom?splitEntries=true&delay=500").to("mock:result1");

                from("atom:file:src/test/data/feed.atom?splitEntries=true&filter=false&delay=500").to("mock:result2");

                from("atom:file:src/test/data/feed.atom?splitEntries=true&filter=true&lastUpdate=#myDate&delay=500").to("mock:result3");
            }
        };
    }
}
