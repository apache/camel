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
package org.apache.camel.test.junit5.patterns;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class AsyncSendMockTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncSendMockTest.class);

    @Override
    public String isMockEndpoints() {
        return "seda*";
    }

    @Test
    public void testMakeAsyncApiCall() {
        try {
            getMockEndpoint("mock:seda:start").expectedHeaderReceived("username", "admin123");
            getMockEndpoint("mock:seda:start").expectedBodiesReceived("Hello");
            DefaultExchange dfex = new DefaultExchange(context);
            dfex.getIn().setHeader("username", "admin123");
            dfex.getIn().setHeader("password", "admin");
            dfex.getIn().setBody("Hello");
            template.asyncSend("seda:start", dfex);
            MockEndpoint.assertIsSatisfied(context);
        } catch (Exception e) {
            LOG.warn("Failed to make async call to api: {}", e.getMessage(), e);
            fail("Failed to make async call to api");
        }
    }
}
