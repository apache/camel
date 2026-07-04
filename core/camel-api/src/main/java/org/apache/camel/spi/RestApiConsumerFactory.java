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
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;

/**
 * SPI that creates the Camel {@link org.apache.camel.Consumer} responsible for handling incoming HTTP GET requests to
 * the REST API documentation endpoint.
 * <p/>
 * When a REST-DSL route includes an {@code apiContextPath} in its {@link RestConfiguration}, Camel uses this factory to
 * create a dedicated consumer that serves the in-memory OpenAPI specification generated from the route model. HTTP
 * components that support the REST DSL (such as {@code camel-jetty}, {@code camel-netty-http}, and
 * {@code camel-platform-http}) implement this interface alongside {@link RestConsumerFactory}. The factory is distinct
 * from {@link RestOpenApiConsumerFactory}, which handles full contract-first OpenAPI spec routing rather than just
 * serving the generated doc listing.
 * <p/>
 * See <a href="https://camel.apache.org/manual/rest-dsl.html">Rest DSL</a> in the Camel user manual.
 *
 * @see RestConsumerFactory
 * @see RestOpenApiConsumerFactory
 * @see RestConfiguration
 */
public interface RestApiConsumerFactory {

    /**
     * Creates a new REST API <a href="https://camel.apache.org/event-driven-consumer.html">Event Driven Consumer</a>,
     * which provides API listing of the REST services
     *
     * @param  camelContext  the camel context
     * @param  processor     the processor
     * @param  contextPath   the context-path
     * @param  configuration REST configuration
     * @param  parameters    additional parameters
     * @return               a newly created REST API consumer
     * @throws Exception     can be thrown
     */
    Consumer createApiConsumer(
            CamelContext camelContext, Processor processor, String contextPath,
            RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception;

}
