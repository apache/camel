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
package org.apache.camel;

/**
 * Global endpoint configurations which can be set as defaults when Camel creates new {@link Endpoint}s.
 */
public interface GlobalEndpointConfiguration {

    boolean isLazyStartProducer();

    /**
     * Whether the producer should be started lazy (on the first message). By starting lazy you can use this to allow CamelContext and routes to startup
     * in situations where a producer may otherwise fail during starting and cause the route to fail being started. By deferring this startup to be lazy then
     * the startup failure can be handled during routing messages via Camel's routing error handlers. Beware that when the first message is processed
     * then creating and starting the producer may take a little time and prolong the total processing time of the processing.
     */
    void setLazyStartProducer(boolean lazyStartProducer);

    boolean isBridgeErrorHandler();

    /**
     * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions occurred while
     * the consumer is trying to pickup incoming messages, or the likes, will now be processed as a message and
     * handled by the routing Error Handler.
     * <p/>
     * By default the consumer will use the org.apache.camel.spi.ExceptionHandler to deal with exceptions,
     * that will be logged at WARN/ERROR level and ignored.
     */
    void setBridgeErrorHandler(boolean bridgeErrorHandler);

    boolean isBasicPropertyBinding();

    /**
     * Whether the endpoint should use basic property binding (Camel 2.x) or the newer property binding with additional capabilities.
     */
    void setBasicPropertyBinding(boolean basicPropertyBinding);

}
