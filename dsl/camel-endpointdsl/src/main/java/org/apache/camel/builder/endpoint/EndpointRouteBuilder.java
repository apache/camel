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
package org.apache.camel.builder.endpoint;

import java.io.Reader;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.apache.camel.util.function.ThrowingConsumer;

/**
 * A {@link RouteBuilder} which gives access to the endpoint DSL.
 */
public abstract class EndpointRouteBuilder extends RouteBuilder implements EndpointBuilderFactory {

    public EndpointRouteBuilder() {
    }

    public EndpointRouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * Add routes to a context using a lambda expression. It can be used as following:
     *
     * <pre>
     * EndpointRouteBuilder.addEndpointRoutes(context, rb -&gt;
     *     rb.from(rb.direct("inbound")).bean(MyBean.class)));
     * </pre>
     *
     * @param  context   the camel context to add routes
     * @param  rbc       a lambda expression receiving the {@code RouteBuilder} to use for creating routes
     * @throws Exception if an error occurs
     */
    public static void addEndpointRoutes(CamelContext context, LambdaEndpointRouteBuilder rbc)
            throws Exception {
        context.addRoutes(new EndpointRouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                rbc.accept(this);
            }
        });
    }

    /**
     * Loads {@link EndpointRouteBuilder} from {@link Resource} using the given consumer to create an
     * {@link EndpointRouteBuilder} instance.
     *
     * @param  resource the resource to be loaded.
     * @param  consumer the function used to create a {@link EndpointRouteBuilder}
     * @return          a {@link EndpointRouteBuilder}
     */
    public static EndpointRouteBuilder loadEndpointRoutesBuilder(
            Resource resource, ThrowingBiConsumer<Reader, EndpointRouteBuilder, Exception> consumer) {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                CamelContextAware.trySetCamelContext(resource, getContext());
                setResource(resource);

                try (Reader reader = resource.getReader()) {
                    consumer.accept(reader, this);
                }
            }
        };
    }

    /**
     * Loads {@link EndpointRouteBuilder} from {@link Resource} using the given consumer to create an
     * {@link EndpointRouteBuilder} instance.
     *
     * @param  consumer the function used to create a {@link EndpointRouteBuilder}
     * @return          a {@link EndpointRouteBuilder}
     */
    public static EndpointRouteBuilder loadEndpointRoutesBuilder(ThrowingConsumer<EndpointRouteBuilder, Exception> consumer) {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                consumer.accept(this);
            }
        };
    }

}
