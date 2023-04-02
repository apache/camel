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

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.cloud.ServiceCallConstants;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.PluginHelper;
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
    private ProcessorFactory processorFactory;
    private AsyncProcessor processor;

    private Expression serviceNameExp;
    private Expression serviceUriExp;
    private Expression servicePathExp;
    private Expression serviceSchemeExp;

    public DefaultServiceCallProcessor(CamelContext camelContext, String name, String scheme, String uri,
                                       ExchangePattern exchangePattern,
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
    protected void doBuild() throws Exception {
        ObjectHelper.notNull(camelContext, "camel context");
        processorFactory = PluginHelper.getProcessorFactory(camelContext);
    }

    @Override
    protected void doInit() throws Exception {
        StringHelper.notEmpty(name, "name", "service name");
        ObjectHelper.notNull(expression, "expression");
        ObjectHelper.notNull(loadBalancer, "load balancer");

        Processor send = processorFactory.createProcessor(camelContext,
                "SendDynamicProcessor", new Object[] { uri, expression, exchangePattern });
        processor = AsyncProcessorConverterHelper.convert(send);

        // optimize and build expressions that are static ahead of time
        Language simple = camelContext.resolveLanguage("simple");
        serviceNameExp = simple.createExpression(name);
        serviceUriExp = uri != null ? simple.createExpression(uri) : null;
        servicePathExp = contextPath != null ? simple.createExpression(contextPath) : null;
        serviceSchemeExp = scheme != null ? simple.createExpression(scheme) : null;
    }

    @Override
    protected void doStart() throws Exception {
        // Start services if needed
        ServiceHelper.startService(processor, loadBalancer);
    }

    @Override
    protected void doStop() throws Exception {
        // Stop services if needed
        ServiceHelper.stopService(loadBalancer, processor);
    }

    // *************************************
    // Processor
    // *************************************

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Message message = exchange.getIn();

        // the values can be dynamic using simple language so compute those
        final String serviceName = serviceNameExp.evaluate(exchange, String.class);
        final String serviceUri = serviceUriExp != null ? serviceUriExp.evaluate(exchange, String.class) : null;
        final String servicePath = servicePathExp != null ? servicePathExp.evaluate(exchange, String.class) : null;
        final String serviceScheme = serviceSchemeExp != null ? serviceSchemeExp.evaluate(exchange, String.class) : null;

        message.setHeader(ServiceCallConstants.SERVICE_CALL_URI, serviceUri);
        message.setHeader(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH, servicePath);
        message.setHeader(ServiceCallConstants.SERVICE_CALL_SCHEME, serviceScheme);
        message.setHeader(ServiceCallConstants.SERVICE_NAME, serviceName);

        try {
            return loadBalancer.process(exchange, serviceName, server -> execute(server, exchange, callback));
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
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
        message.getHeaders().compute(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH,
                (k, v) -> v == null ? meta.get(ServiceDefinition.SERVICE_META_PATH) : v);

        // If port is not set on service call definition, reuse the one from
        // ServiceDefinition, if any
        message.getHeaders().compute(ServiceCallConstants.SERVICE_PORT,
                (k, v) -> v == null ? meta.get(ServiceDefinition.SERVICE_META_PORT) : v);

        // use the dynamic send processor to call the service
        return processor.process(exchange, callback);
    }

}
