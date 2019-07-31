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
package org.apache.camel.test.patterns;

import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class AsyncSendMockTest extends CamelTestSupport {
    @Override
    public String isMockEndpoints() {
        return "seda*";
    }

    @Test
    public void testmakeAsyncApiCall() {
        try {
            getMockEndpoint("mock:seda:start").expectedHeaderReceived("username", "admin123");
            getMockEndpoint("mock:seda:start").expectedBodiesReceived("Hello");
            DefaultExchange dfex = new DefaultExchange(context);
            dfex.getIn().setHeader("username", "admin123");
            dfex.getIn().setHeader("password", "admin");
            dfex.getIn().setBody("Hello");
            template.asyncSend("seda:start", dfex);
            assertMockEndpointsSatisfied();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue("Failed to make async call to api", false);
        }
    }
}
