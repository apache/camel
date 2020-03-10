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
package org.apache.camel.builder;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Test;

public class NotifyBuilderFromRouteTest extends ContextTestSupport {

    @Test
    public void testDoneFromRoute() throws Exception {
        // notify when exchange is done
        NotifyBuilder builder = new NotifyBuilder(context).fromRoute("foo").whenDone(1);
        builder.create();

        template.sendBody("seda:foo", "Hello world!");

        assertTrue(builder.matchesMockWaitTime());
    }

    @Test
    public void testDoneFromCurrentRoute() throws Exception {
        // notify when exchange is done
        NotifyBuilder builder = new NotifyBuilder(context).fromCurrentRoute("bar").whenDone(1);
        builder.create();

        template.sendBody("seda:foo", "Hello world!");

        assertTrue(builder.matchesMockWaitTime());
    }

    @Test
    public void testDoneFromCurrentRouteStartRoute() throws Exception {
        // notify when exchange is done
        NotifyBuilder builder = new NotifyBuilder(context).fromCurrentRoute("foo").whenDone(1);
        builder.create();

        template.sendBody("seda:foo", "Hello world!");

        assertTrue(builder.matchesMockWaitTime());
    }

    @Override
    protected Registry createRegistry() throws Exception {
        final Registry registry = super.createRegistry();
        registry.bind("proxy", new ProxyComponent());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("proxy:seda:foo").routeId("foo").to("direct:bar").to("mock:foo");

                from("direct:bar").routeId("bar").to("mock:bar");
            }
        };
    }

    private static final class ProxyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new ProxyEndpoint(this, uri, remaining);
        }

    }

    private static final class ProxyEndpoint extends DefaultEndpoint {

        private final Endpoint target;

        private ProxyEndpoint(ProxyComponent component, String uri, String target) {
            super(uri, component);
            this.target = getCamelContext().getEndpoint(target);
        }

        @Override
        public Producer createProducer() throws Exception {
            return target.createProducer();
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return target.createConsumer(processor);
        }

        @Override
        public boolean isSingleton() {
            return target.isSingleton();
        }
    }
}
