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
package org.apache.camel.component.langchain4j.service;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;

/**
 * Base for the tests that bind their AI Service into the registry and then build the route.
 * <p>
 * The producer resolves the service when it starts, so the route cannot exist before the registry entry does: every
 * test here binds first and adds the route second, which is why the route builder is not installed automatically.
 */
public abstract class LangChain4jServiceTestSupport extends CamelTestSupport {

    protected LangChain4jServiceTestSupport() {
        testConfiguration().withUseRouteBuilder(false);
    }

    /**
     * Adds {@code from("direct:start").to(endpointUri)} without starting the context, for a test that asserts on
     * startup failure.
     *
     * @param endpointUri the endpoint under test
     */
    protected void addRouteTo(String endpointUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(endpointUri);
            }
        });
    }

    /**
     * Adds the route and starts the context, for a test that goes on to send a message.
     *
     * @param endpointUri the endpoint under test
     */
    protected void startWithRouteTo(String endpointUri) throws Exception {
        addRouteTo(endpointUri);
        context.start();
    }
}
