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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

public abstract class InterceptFromRouteTestSupport extends ContextTestSupport {
    protected MockEndpoint a;
    protected MockEndpoint b;

    public void testSendMatchingMessage() throws Exception {
        prepareMatchingTest();
        template.sendBodyAndHeader("direct:start", "<matched/>", "foo", "bar");
        assertMockEndpointsSatisfied();
    }

    public void testSendNonMatchingMessage() throws Exception {
        prepareNonMatchingTest();
        template.sendBodyAndHeader("direct:start", "<notMatched/>", "foo", "notMatchedHeaderValue");
        assertMockEndpointsSatisfied();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
    }

    protected abstract void prepareMatchingTest();

    protected abstract void prepareNonMatchingTest();
}
