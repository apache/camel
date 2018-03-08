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
package org.apache.camel.impl.cloud;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServiceCallProcessor extends ServiceSupport implements AsyncProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServiceCallProcessor.class);

    private final ExchangePattern exchangePattern;
    private final String name;
    private final String scheme;
    private final String uri;
    private final String contextPath;
    private final CamelContext camelContext;
    private final ServiceLoadBalancer loadBalancer;
    private final Expression expression;
    private SendDynamicProcessor processor;

    public DefaultServiceCallProcessor(
        CamelContext camelContext, String name, String scheme, String uri, ExchangePattern exchangePattern,
        ServiceLoadBalancer loadBalancer, Expression expression) {

        this.uri = uri;
        this.exchangePattern = exchangePattern;
        this.camelContext = camelContext;
        this.loadBalancer = loadBalancer;

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

        this.expression = expression;
    }

    // *************************************
    // Properties
    // *************************************


    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    public String getName() {
        return name;
    }

    public String getScheme() {
        return scheme;
    }

    public String getUri() {
        return uri;
    }

    public String getContextPath() {
        return contextPath;
    }

    public ServiceLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public Expression getExpression() {
        return expression;
    }

    // *************************************
    // Lifecycle
    // *************************************

    @Override
    protected void doStart() throws Exception {
        StringHelper.notEmpty(name, "name", "service name");
        ObjectHelper.notNull(camelContext, "camel context");
        ObjectHelper.notNull(expression, "expression");
        ObjectHelper.notNull(loadBalancer, "load balancer");

        processor = new SendDynamicProcessor(uri, expression);
        processor.setCamelContext(camelContext);
        if (exchangePattern != null) {
            processor.setPattern(exchangePattern);
        }

        // Start services if needed
        ServiceHelper.startService(processor);
        ServiceHelper.startService(loadBalancer);
    }

    @Override
    protected void doStop() throws Exception {
        // Stop services if needed
        ServiceHelper.stopService(loadBalancer);
        ServiceHelper.stopService(processor);
    }

    // *************************************
    // Processor
    // *************************************


    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Message message = exchange.getIn();

        // the values can be dynamic using simple language so compute those
        String val = uri;
        if (SimpleLanguage.hasSimpleFunction(val)) {
            val = SimpleLanguage.simple(val).evaluate(exchange, String.class);
        }
        message.setHeader(ServiceCallConstants.SERVICE_CALL_URI, val);

        val = contextPath;
        if (SimpleLanguage.hasSimpleFunction(val)) {
            val = SimpleLanguage.simple(val).evaluate(exchange, String.class);
        }
        message.setHeader(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH, val);

        val = scheme;
        if (SimpleLanguage.hasSimpleFunction(val)) {
            val = SimpleLanguage.simple(val).evaluate(exchange, String.class);
        }
        message.setHeader(ServiceCallConstants.SERVICE_CALL_SCHEME, val);

        String serviceName = name;
        if (SimpleLanguage.hasSimpleFunction(serviceName)) {
            serviceName = SimpleLanguage.simple(serviceName).evaluate(exchange, String.class);
        }
        message.setHeader(ServiceCallConstants.SERVICE_NAME, serviceName);

        try {
            return loadBalancer.process(serviceName, server -> execute(server, exchange, callback));
        } catch (Exception e) {
            exchange.setException(e);
            return true;
        }
    }

    private boolean execute(ServiceDefinition server, Exchange exchange, AsyncCallback callback) throws Exception {
        String host = server.getHost();
        int port = server.getPort();

        LOGGER.debug("Service {} active at server: {}:{}", name, host, port);

        // set selected server as header
        exchange.getIn().setHeader(ServiceCallConstants.SERVICE_HOST, host);
        exchange.getIn().setHeader(ServiceCallConstants.SERVICE_PORT, port > 0 ? port : null);
        exchange.getIn().setHeader(ServiceCallConstants.SERVICE_NAME, server.getName());
        exchange.getIn().setHeader(ServiceCallConstants.SERVICE_META, server.getMetadata());

        // use the dynamic send processor to call the service
        return processor.process(exchange, callback);
    }
}
