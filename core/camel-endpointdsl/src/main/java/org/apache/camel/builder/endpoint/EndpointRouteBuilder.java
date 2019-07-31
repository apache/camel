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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
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
     * Add routes to a context using a lambda expression.
     * It can be used as following:
     * <pre>
     * RouteBuilder.addRoutes(context, rb ->
     *     rb.from("direct:inbound").bean(ProduceTemplateBean.class)));
     * </pre>
     *
     * @param context the camel context to add routes
     * @param rbc a lambda expression receiving the {@code RouteBuilder} to use for creating routes
     * @throws Exception if an error occurs
     */
    public static void addEndpointRoutes(CamelContext context, ThrowingConsumer<EndpointRouteBuilder, Exception> rbc) throws Exception {
        context.addRoutes(new EndpointRouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                rbc.accept(this);
            }
        });
    }

}
