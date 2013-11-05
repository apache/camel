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
package org.apache.camel.component.timer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class TimerWithTimeOptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testFiredInFutureWithTPattern() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?time=%s", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        // period is default 1000 so we can get more messages
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureWithTPatternNoPeriod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?period=0&time=%s", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureWithTPatternFixedRate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?fixedRate=true&time=%s", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        // period is default 1000 so we can get more messages
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureWithoutTPattern() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?time=%s", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        // period is default 1000 so we can get more messages
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureWithoutTPatternNoPeriod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?period=0&time=%s", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureCustomPattern() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?time=%s&pattern=dd-MM-yyyy HH:mm:ss", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        // period is default 1000 so we can get more messages
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureCustomPatternNoPeriod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Date future = new Date(new Date().getTime() + 1000);

                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                String time = sdf.format(future);

                fromF("timer://foo?period=0&time=%s&pattern=dd-MM-yyyy HH:mm:ss", time).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    public void testFiredInFutureIllegalTime() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("timer://foo?time=%s", "20090101").to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should throw an exception");
        } catch (FailedToCreateRouteException e) {
            assertIsInstanceOf(ParseException.class, e.getCause().getCause());
        }
    }

}
