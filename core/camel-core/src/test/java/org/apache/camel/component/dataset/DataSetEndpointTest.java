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
package org.apache.camel.component.dataset;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;
import org.junit.Test;

public class DataSetEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDataSetEndpoint() throws Exception {
        final DataSetEndpoint endpoint = new DataSetEndpoint("dataset://foo", null, new SimpleDataSet(2));
        endpoint.setCamelContext(context);
        endpoint.setInitialDelay(0);

        Assert.assertEquals(0, endpoint.getPreloadSize());
        Assert.assertEquals(0, endpoint.getConsumeDelay());
        Assert.assertEquals(3, endpoint.getProduceDelay());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(endpoint).to("direct:foo");
                from("direct:foo").to(endpoint);
            }
        });
        context.start();

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testDataSetEndpointCtr() throws Exception {
        final DataSetEndpoint endpoint = new DataSetEndpoint("dataset://foo", context.getComponent("dataset"), new SimpleDataSet(2));

        endpoint.setConsumeDelay(2);
        Assert.assertEquals(2, endpoint.getConsumeDelay());
        endpoint.setProduceDelay(5);
        Assert.assertEquals(5, endpoint.getProduceDelay());
        endpoint.setInitialDelay(1);
        Assert.assertEquals(1, endpoint.getInitialDelay());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(endpoint).to("direct:foo");
                from("direct:foo").to(endpoint);
            }
        });
        context.start();

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testDataSetReporter() throws Exception {
        final DataSetEndpoint endpoint = new DataSetEndpoint("dataset://foo", context.getComponent("dataset"), new SimpleDataSet(10));
        endpoint.setInitialDelay(0);

        final AtomicBoolean reported = new AtomicBoolean(false);
        endpoint.setReporter(new Processor() {
            public void process(Exchange exchange) throws Exception {
                reported.set(true);
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(endpoint).to("direct:foo");
                from("direct:foo").to(endpoint);
            }
        });
        context.start();

        endpoint.assertIsSatisfied();
        Assert.assertTrue(reported.get());
    }

    @Test
    public void testSimpleDataSet() throws Exception {
        SimpleDataSet ds = new SimpleDataSet();
        ds.setSize(2);
        ds.setDefaultBody("Hi");
        Assert.assertEquals("Hi", ds.getDefaultBody());
    }

    @Test
    public void testDataSetSupport() throws Exception {
        MyDataSet ds = new MyDataSet();
        ds.setSize(4);
        ds.setReportCount(0);
        ds.setOutputTransformer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                String body = "Hi " + exchange.getIn().getBody(String.class);
                exchange.getIn().setBody(body);
            }
        });
        Assert.assertNotNull(ds.getOutputTransformer());

        final DataSetEndpoint endpoint = new DataSetEndpoint("dataset://foo", context.getComponent("dataset"), ds);
        endpoint.setInitialDelay(0);
        endpoint.allMessages().body().startsWith("Hi ");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(endpoint).to("direct:foo");
                from("direct:foo").to(endpoint);
            }
        });
        context.start();

        endpoint.assertIsSatisfied();
    }

    private static class MyDataSet extends DataSetSupport {

        @Override
        protected Object createMessageBody(long messageIndex) {
            return "Message " + messageIndex;
        }
    }

}
