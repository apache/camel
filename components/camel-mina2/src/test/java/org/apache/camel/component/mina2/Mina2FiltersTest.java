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
package org.apache.camel.component.mina2;

import java.util.ArrayList;
import java.util.List;
import javax.naming.Context;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.junit.Test;

/**
 * For unit testing the <tt>filters</tt> option.
 */
public class Mina2FiltersTest extends BaseMina2Test {

    @Test
    public void testFilterListRef() throws Exception {
        testFilter(String.format("mina2:tcp://localhost:%1$s?textline=true&minaLogger=true&sync=false&filters=#myFilters", getPort()));
    }

    @Test
    public void testFilterElementRef() throws Exception {
        testFilter(String.format("mina2:tcp://localhost:%1$s?textline=true&minaLogger=true&sync=false&filters=#myFilter", getPort()));
    }

    @Override
    public void tearDown() throws Exception {
        TestFilter.called = 0;
        super.tearDown();
    }

    private void testFilter(final String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {

            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        assertMockEndpointsSatisfied();

        assertEquals("The filter should have been called twice (producer and consumer)", 2, TestFilter.called);

        producer.stop();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        IoFilter myFilter = new TestFilter();
        List<IoFilter> myFilters = new ArrayList<IoFilter>();
        myFilters.add(myFilter);

        answer.bind("myFilters", myFilters);
        answer.bind("myFilter", myFilter);
        return answer;
    }

    public static final class TestFilter extends IoFilterAdapter {

        public static volatile int called;

        @Override
        public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
            incCalled();
            nextFilter.sessionCreated(session);
        }

        public static synchronized void incCalled() {
            called++;
        }
    }
}
