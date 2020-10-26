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
package org.apache.camel.processor;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.impl.engine.CamelInternalProcessor;
import org.apache.camel.impl.engine.DefaultChannel;
import org.apache.camel.impl.engine.SharedCamelInternalProcessor;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.InternalProcessorFactory;
import org.apache.camel.spi.SharedInternalProcessor;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.annotations.JdkService;

@JdkService(InternalProcessorFactory.FACTORY)
public class DefaultInternalProcessorFactory implements InternalProcessorFactory {

    public InternalProcessor addUnitOfWorkProcessorAdvice(CamelContext camelContext, Processor processor, Route route) {
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));
        return internal;
    }

    public InternalProcessor addChildUnitOfWorkProcessorAdvice(
            CamelContext camelContext, Processor processor, Route route, UnitOfWork parent) {
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, processor);
        internal.addAdvice(new CamelInternalProcessor.ChildUnitOfWorkProcessorAdvice(route, camelContext, parent));
        return internal;
    }

    public SharedInternalProcessor createSharedCamelInternalProcessor(CamelContext camelContext) {
        return new SharedCamelInternalProcessor(
                camelContext, new CamelInternalProcessor.UnitOfWorkProcessorAdvice(null, camelContext));
    }

    public Channel createChannel(CamelContext camelContext) {
        return new DefaultChannel(camelContext);
    }

    public AsyncProducer createInterceptSendToEndpointProcessor(
            InterceptSendToEndpoint endpoint, Endpoint delegate, AsyncProducer producer, boolean skip) {
        return new InterceptSendToEndpointProcessor(endpoint, delegate, producer, skip);
    }

    public AsyncProcessor createWrapProcessor(Processor processor, Processor wrapped) {
        return new WrapProcessor(processor, wrapped);
    }

    public AsyncProducer createUnitOfWorkProducer(Producer producer) {
        return new UnitOfWorkProducer(producer);
    }

}
