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
package org.apache.camel.component.spring.batch.support;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CamelItemProcessorTest extends CamelTestSupport {

    // Fixtures

    CamelItemProcessor<String, String> camelItemProcessor;

    String message = "message";

    // Camel fixtures

    @Override
    protected void doPostSetup() throws Exception {
        camelItemProcessor = new CamelItemProcessor<>(template(), "direct:start");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setExchangePattern(ExchangePattern.InOut).
                        setBody().simple("${body}${body}");
            }
        };
    }

    // Tests

    @Test
    public void shouldReturnDoubledMessage() throws Exception {
        // When
        String messageRead = camelItemProcessor.process(message);

        // Then
        assertEquals(message + message, messageRead);
    }

}
