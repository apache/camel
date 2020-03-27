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
package org.apache.camel.impl.cloud;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.cloud.ServiceCallConstants;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.spi.Language;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServiceCallProcessor extends AsyncProcessorSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceCallProcessor.class);

    private final ExchangePattern exchangePattern;
    private final String name;
    private final String scheme;
    private final String uri;
    private final String contextPath;
    private final CamelContext camelContext;
    private final ServiceLoadBalancer loadBalancer;
    private final Expression expression;
    private AsyncProcessor processor;

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
    protected void doInit() throws Exception {
        StringHelper.notEmpty(name, "name", "service name");
        ObjectHelper.notNull(camelContext, "camel context");
        ObjectHelper.notNull(expression, "expression");
        ObjectHelper.notNull(loadBalancer, "load balancer");

        Map<String, Object> args = new HashMap<>();
        args.put("uri", uri);
        args.put("expression", expression);
        args.put("exchangePattern", exchangePattern);

        Processor send = camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory().createProcessor(camelContext, "SendDynamicProcessor", args);
        processor = AsyncProcessorConverterHelper.convert(send);
    }

    @Override
    protected void doStart() throws Exception {
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
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Message message = exchange.getIn();

        // the values can be dynamic using simple language so compute those
        final String serviceName = applySimpleLanguage(name, exchange);
        final String serviceUri = applySimpleLanguage(uri, exchange);
        final String servicePath = applySimpleLanguage(contextPath, exchange);
        final String serviceScheme = applySimpleLanguage(scheme, exchange);

        message.setHeader(ServiceCallConstants.SERVICE_CALL_URI, serviceUri);
        message.setHeader(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH, servicePath);
        message.setHeader(ServiceCallConstants.SERVICE_CALL_SCHEME, serviceScheme);
        message.setHeader(ServiceCallConstants.SERVICE_NAME, serviceName);

        try {
            return loadBalancer.process(serviceName, server -> execute(server, exchange, callback));
        } catch (Exception e) {
            exchange.setException(e);
            return true;
        }
    }

    private boolean execute(ServiceDefinition service, Exchange exchange, AsyncCallback callback) throws Exception {
        final Message message = exchange.getIn();
        final String host = service.getHost();
        final int port = service.getPort();
        final Map<String, String> meta = service.getMetadata();

        LOG.debug("Service {} active at server: {}:{}", name, host, port);

        // set selected server as header
        message.setHeader(ServiceCallConstants.SERVICE_HOST, host);
        message.setHeader(ServiceCallConstants.SERVICE_PORT, port > 0 ? port : null);
        message.setHeader(ServiceCallConstants.SERVICE_NAME, service.getName());
        message.setHeader(ServiceCallConstants.SERVICE_META, meta);

        // If context path is not set on service call definition, reuse the one from
        // ServiceDefinition, if any
        message.getHeaders().compute(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH, (k, v) ->
            v == null ? meta.get(ServiceDefinition.SERVICE_META_PATH) : v
        );

        // If port is not set on service call definition, reuse the one from
        // ServiceDefinition, if any
        message.getHeaders().compute(ServiceCallConstants.SERVICE_PORT, (k, v) ->
            v == null ? meta.get(ServiceDefinition.SERVICE_META_PORT) : v
        );

        // use the dynamic send processor to call the service
        return processor.process(exchange, callback);
    }

    /**
     * This function applies the simple language to the given expression.
     *
     * @param expression the expression
     * @param exchange the exchange
     * @return the computed expression
     */
    private static String applySimpleLanguage(String expression, Exchange exchange) {
        if (expression != null) {
            Language simple = exchange.getContext().resolveLanguage("simple");
            return simple.createExpression(expression).evaluate(exchange, String.class);
        } else {
            return null;
        }
    }
}
