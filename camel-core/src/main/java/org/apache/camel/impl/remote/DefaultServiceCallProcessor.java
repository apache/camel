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
package org.apache.camel.impl.remote;

import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Traceable;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.support.ServiceCallExpressionSupport;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServiceCallProcessor extends ServiceSupport implements AsyncProcessor, CamelContextAware, Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceCallProcessor.class);

    private final ServiceCallExpressionSupport serviceCallExpression;
    private final ExchangePattern exchangePattern;
    private final String uri;
    private final String name;
    private final String scheme;
    private final String contextPath;
    private CamelContext camelContext;
    private String id;
    private ServiceCallServerListStrategy<ServiceCallServer> serverListStrategy;
    private ServiceCallLoadBalancer<ServiceCallServer> loadBalancer;
    private SendDynamicProcessor processor;

    public DefaultServiceCallProcessor(String name, String scheme, String uri, ExchangePattern exchangePattern) {
        this.uri = uri;
        this.exchangePattern = exchangePattern;

        // setup from the provided name which can contain scheme and context-path information as well
        String serviceName;
        if (name.contains("/")) {
            serviceName = ObjectHelper.before(name, "/");
            this.contextPath = ObjectHelper.after(name, "/");
        } else if (name.contains("?")) {
            serviceName = ObjectHelper.before(name, "?");
            this.contextPath = ObjectHelper.after(name, "?");
        } else {
            serviceName = name;
            this.contextPath = null;
        }
        if (serviceName.contains(":")) {
            this.scheme = ObjectHelper.before(serviceName, ":");
            this.name = ObjectHelper.after(serviceName, ":");
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

    public ServiceCallLoadBalancer<ServiceCallServer> getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(ServiceCallLoadBalancer<ServiceCallServer> loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public DefaultServiceCallProcessor loadBalancer(ServiceCallLoadBalancer<ServiceCallServer> loadBalancer) {
        setLoadBalancer(loadBalancer);
        return this;
    }

    public ServiceCallServerListStrategy<ServiceCallServer> getServerListStrategy() {
        return serverListStrategy;
    }

    public void setServerListStrategy(ServiceCallServerListStrategy<ServiceCallServer> serverListStrategy) {
        this.serverListStrategy = serverListStrategy;
    }

    public DefaultServiceCallProcessor serverListStrategy(ServiceCallServerListStrategy<ServiceCallServer> serverListStrategy) {
        setServerListStrategy(serverListStrategy);
        return this;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(serverListStrategy, "serverListStrategy");
        ObjectHelper.notNull(loadBalancer, "loadBalancer");


        LOG.info("ConsulsServiceCall at dc: {} with service name: {} is using load balancer: {} and service discovery: {}",
            name, loadBalancer, serverListStrategy);

        processor = new SendDynamicProcessor(uri, serviceCallExpression);
        processor.setCamelContext(getCamelContext());
        if (exchangePattern != null) {
            processor.setPattern(exchangePattern);
        }

        ServiceHelper.startServices(serverListStrategy, processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor, serverListStrategy);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Collection<ServiceCallServer> servers = null;
        String serviceName = exchange.getIn().getHeader(ServiceCallConstants.SERVICE_NAME, name, String.class);
        try {
            servers = serverListStrategy.getUpdatedListOfServers(serviceName);
            if (servers == null || servers.isEmpty()) {
                exchange.setException(new RejectedExecutionException("No active services with name " + name));
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            callback.done(true);
            return true;
        }

        // let the client load balancer chose which server to use
        ServiceCallServer server = loadBalancer.chooseServer(servers);
        String ip = server.getIp();
        int port = server.getPort();
        LOG.debug("Service {} active at server: {}:{}", name, ip, port);

        // set selected server as header
        exchange.getIn().setHeader(ServiceCallConstants.SERVER_IP, ip);
        exchange.getIn().setHeader(ServiceCallConstants.SERVER_PORT, port);

        // use the dynamic send processor to call the service
        return processor.process(exchange, callback);
    }
}