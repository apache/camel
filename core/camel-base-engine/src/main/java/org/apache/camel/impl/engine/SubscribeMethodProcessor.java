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
package org.apache.camel.impl.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.spi.Language;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link Processor} which is used for POJO @Consume where you can have multiple @Consume on the same
 * endpoint/consumer and via predicate's can filter and call different methods.
 */
public final class SubscribeMethodProcessor extends AsyncProcessorSupport implements Navigate<Processor> {

    private final Endpoint endpoint;
    private final Map<AsyncProcessor, Predicate> methods = new LinkedHashMap<>();
    private Language simple;

    public SubscribeMethodProcessor(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void addMethod(final Object pojo, final Method method, final Endpoint endpoint, String predicate) throws Exception {
        Processor answer = PluginHelper.getBeanProcessorFactory(endpoint.getCamelContext())
                .createBeanProcessor(endpoint.getCamelContext(), pojo, method);

        // must ensure the consumer is being executed in an unit of work so synchronization callbacks etc is invoked
        answer = PluginHelper.getInternalProcessorFactory(endpoint.getCamelContext())
                .addUnitOfWorkProcessorAdvice(endpoint.getCamelContext(), answer, null);
        Predicate p;
        if (ObjectHelper.isEmpty(predicate)) {
            p = PredicateBuilder.constant(true);
        } else {
            p = simple.createPredicate(predicate);
        }
        methods.put((AsyncProcessor) answer, p);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            // evaluate which predicate matches and call the method
            for (Map.Entry<AsyncProcessor, Predicate> entry : methods.entrySet()) {
                Predicate predicate = entry.getValue();
                if (predicate.matches(exchange)) {
                    return entry.getKey().process(exchange, callback);
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        simple = getEndpoint().getCamelContext().resolveLanguage("simple");
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(methods.keySet());
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(methods.keySet());
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(methods.keySet());
    }

    @Override
    public String toString() {
        return "SubscribeMethodProcessor[" + endpoint + "]";
    }

    @Override
    public List<Processor> next() {
        return new ArrayList<>(methods.keySet());
    }

    @Override
    public boolean hasNext() {
        return !methods.isEmpty();
    }
}
