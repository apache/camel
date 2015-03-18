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
package org.apache.camel.impl;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class PojoProduceProxyInterceptEndpointTest extends TestCase {

    public void testPojoProduceInterceptAlreadyStarted() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:start")
                        .to("language:simple:${body}${body}");

                from("direct:start")
                    .to("mock:result");
            }
        });

        // start Camel before POJO being injected
        context.start();

        // use the injector (will use the default)
        // which should post process the bean to inject the @Produce
        MyBean bean = context.getInjector().newInstance(MyBean.class);

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("WorldWorld");

        Object reply = bean.doSomething("World");
        assertEquals("WorldWorld", reply);

        mock.assertIsSatisfied();

        context.stop();
    }

    public void testPojoProduceInterceptNotStarted() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:start")
                        .to("language:simple:${body}${body}");

                from("direct:start")
                    .to("mock:result");
            }
        });

        // use the injector (will use the default)
        // which should post process the bean to inject the @Produce
        MyBean bean = context.getInjector().newInstance(MyBean.class);

        // do NOT start Camel before POJO being injected
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("WorldWorld");

        Object reply = bean.doSomething("World");
        assertEquals("WorldWorld", reply);

        mock.assertIsSatisfied();

        context.stop();
    }

    public interface EchoService {
        String echo(String word);
    }

    public static class MyBean {

        @Produce(uri = "direct:start")
        private EchoService echo;

        public Object doSomething(String body) throws Exception {
            return echo.echo(body);
        }
    }

}
