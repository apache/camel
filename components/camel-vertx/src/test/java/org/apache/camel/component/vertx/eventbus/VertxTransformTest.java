/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx.eventbus;

import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.vertx.VertxBaseTestSupport;
import org.junit.Test;

public class VertxTransformTest extends VertxBaseTestSupport {

    private Vertx vertx;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        vertx = Vertx.vertx();
        vertx.eventBus().registerCodec(new VertxExchangeCodec());

        VertxProcessorFactory pf = new VertxProcessorFactory(vertx);
        context.setProcessorFactory(pf);

        VertxCamelProducer vcp = new VertxCamelProducer(context, vertx, VertxCamelProducer.class.getName());
        context.addService(vcp);

        VertxCamelTransform vct = new VertxCamelTransform(context, vertx, VertxCamelTransform.class.getName());
        context.addService(vct);

        return context;
    }

    @Test
    public void testVertxTransform() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:foo")
                    .transform(simple("Hello ${body}"))
                    .to("mock:bar");
            }
        };
    }
}
