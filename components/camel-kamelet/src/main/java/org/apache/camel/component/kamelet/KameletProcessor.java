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
package org.apache.camel.component.kamelet;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Kamelet EIP implementation.
 */
@ManagedResource(description = "Managed Kamelet Processor")
public class KameletProcessor extends AsyncProcessorSupport
        implements CamelContextAware, Navigate<Processor>, org.apache.camel.Traceable, IdAware, RouteIdAware {

    private final String name;
    private final AsyncProcessor processor;
    private KameletProducer producer;
    private KameletComponent component;
    private CamelContext camelContext;
    private String id;
    private String routeId;

    public KameletProcessor(CamelContext camelContext, String name, Processor processor) {
        this.camelContext = camelContext;
        this.name = name;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @ManagedAttribute(description = "Kamelet name (templateId/routeId?options)")
    public String getName() {
        return name;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return producer.process(exchange, callback);
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        answer.add(processor);
        return answer;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public String getTraceLabel() {
        return "kamelet";
    }

    @Override
    protected void doBuild() throws Exception {
        if (component == null) {
            component = camelContext.getComponent("kamelet", KameletComponent.class);
        }
        if (producer == null) {
            producer = (KameletProducer) camelContext.getEndpoint("kamelet://" + name).createAsyncProducer();
        }
        if (producer != null) {
            ((RouteIdAware) producer).setRouteId(getRouteId());
        }
        ServiceHelper.buildService(processor, producer);

        // we use the kamelet component (producer) to call the kamelet
        // and to receive the reply we register ourselves to the kamelet component
        // with our child processor it should call
        component.addKameletEip(producer.getKey(), processor);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(processor, producer);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor, producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor, producer);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processor, producer);
        component.removeKameletEip(producer.getKey());
    }
}
