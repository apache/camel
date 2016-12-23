/**
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

package org.apache.camel.spring.cloud.servicecall;

import java.io.IOException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Traceable;
import org.apache.camel.impl.remote.DefaultServiceCallExpression;
import org.apache.camel.impl.remote.ServiceCallConstants;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

public class CamelCloudServiceCallProcessor extends ServiceSupport implements AsyncProcessor, CamelContextAware, Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelCloudServiceCallProcessor.class);

    private final ExchangePattern exchangePattern;
    private final String name;
    private final String scheme;
    private final String uri;
    private final String contextPath;
    private final LoadBalancerClient loadBalancerClient;
    private CamelContext camelContext;
    private String id;
    private Expression serviceCallExpression;
    private SendDynamicProcessor processor;

    public CamelCloudServiceCallProcessor(String name, String scheme, String uri, ExchangePattern exchangePattern, LoadBalancerClient loadBalancerClient) {
        this.uri = uri;
        this.exchangePattern = exchangePattern;
        this.loadBalancerClient = loadBalancerClient;

        // setup from the provided name which can contain scheme and context-path information as well
        String serviceName;
        if (name.contains("/")) {
            serviceName = StringHelper.before(name, "/");
            this.contextPath = StringHelper.after(name, "/");
        } else if (name.contains("?")) {
            serviceName = StringHelper.before(name, "?");
            this.contextPath = StringHelper.after(name, "?");
        } else {
            serviceName = name;
            this.contextPath = null;
        }
        if (serviceName.contains(":")) {
            this.scheme = StringHelper.before(serviceName, ":");
            this.name = StringHelper.after(serviceName, ":");
        } else {
            this.scheme = scheme;
            this.name = serviceName;
        }

        this.serviceCallExpression = new DefaultServiceCallExpression(
            this.name,
            this.scheme,
            this.contextPath,
            this.uri);
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
    public String getTraceLabel() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getScheme() {
        return scheme;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getUri() {
        return uri;
    }

    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    public void setServiceCallExpression(Expression serviceCallExpression) {
        this.serviceCallExpression = serviceCallExpression;
    }

    public Expression getServiceCallExpression() {
        return serviceCallExpression;
    }

    public CamelCloudServiceCallProcessor serviceCallExpression(Expression serviceCallExpression) {
        setServiceCallExpression(serviceCallExpression);
        return this;
    }

    public AsyncProcessor getProcessor() {
        return processor;
    }

    @Override
    protected void doStart() throws Exception {
        StringHelper.notEmpty(getName(), "name", "serviceName");
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(serviceCallExpression, "serviceCallExpression");
        ObjectHelper.notNull(loadBalancerClient, "loadBalancerClient");

        LOG.info("ServiceCall with service name: {}", name);

        processor = new SendDynamicProcessor(uri, serviceCallExpression);
        processor.setCamelContext(getCamelContext());
        if (exchangePattern != null) {
            processor.setPattern(exchangePattern);
        }

        ServiceHelper.startServices(processor);
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (exchange.getException() != null) {
            callback.done(true);
            return true;
        }

        try {
            return loadBalancerClient.execute(
                exchange.getIn().getHeader(ServiceCallConstants.SERVICE_NAME, name, String.class),
                instance -> {
                    exchange.getIn().setHeader(ServiceCallConstants.SERVER_IP, instance.getHost());
                    exchange.getIn().setHeader(ServiceCallConstants.SERVER_PORT, instance.getPort());
                    exchange.getIn().setHeader(ServiceCallConstants.SERVICE_NAME, instance.getServiceId());
                    return processor.process(exchange, callback);
                }
            );
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }
}