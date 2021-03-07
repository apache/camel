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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;

/**
 * A factory used internally by Camel to create {@link Processor} and other internal building blocks. This factory is
 * used to have loose coupling between the modules in core.
 *
 * Camel end user should NOT use this, but use {@link ProcessorFactory} instead.
 *
 * @see ProcessorFactory
 */
public interface InternalProcessorFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "internal-processor-factory";

    InternalProcessor addUnitOfWorkProcessorAdvice(CamelContext camelContext, Processor processor, Route route);

    InternalProcessor addChildUnitOfWorkProcessorAdvice(
            CamelContext camelContext, Processor processor, Route route, UnitOfWork parent);

    SharedInternalProcessor createSharedCamelInternalProcessor(CamelContext camelContext);

    Channel createChannel(CamelContext camelContext);

    AsyncProducer createInterceptSendToEndpointProcessor(
            InterceptSendToEndpoint endpoint, Endpoint delegate, AsyncProducer producer, boolean skip);

    AsyncProcessor createWrapProcessor(Processor processor, Processor wrapped);

    AsyncProducer createUnitOfWorkProducer(Producer producer);

}
