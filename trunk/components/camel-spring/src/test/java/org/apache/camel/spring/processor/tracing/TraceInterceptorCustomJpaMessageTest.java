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
package org.apache.camel.spring.processor.tracing;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.TraceInterceptorCustomJpaMessage;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TraceInterceptorCustomJpaMessageTest extends TracingTestBase {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/tracing/traceInterceptorCustomJpaMessageContext.xml");
    }

    @Override
    protected void prepareTestTracerExceptionInOut() {
        MockEndpoint traces = getMockEndpoint("mock:jpa-trace");
        traces.expectedMessageCount(14);
    }

    @Override
    protected void validateTestTracerExceptionInOut() {
        MockEndpoint traces = getMockEndpoint("mock:jpa-trace");
        assertEquals(14, traces.getExchanges().size());
        for (Exchange exchange : traces.getExchanges()) {
            assertEquals(exchange.getIn().getBody().getClass(), TraceInterceptorCustomJpaMessage.class);
        }
    }

    @Override
    protected int getMessageCount() {
        MockEndpoint traces = getMockEndpoint("mock:jpa-trace");
        return traces.getExchanges().size();
    }

}
