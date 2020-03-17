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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.RestComponentHelper;
import org.apache.camel.support.RestProducerFactoryHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP based component.
 */
@Component("netty-http")
public class NettyHttpComponent extends NettyComponent implements HeaderFilterStrategyAware, RestConsumerFactory, RestApiConsumerFactory, RestProducerFactory, SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpComponent.class);

    // factories which is created by this component and therefore manage their lifecycles
    private final Map<Integer, HttpServerConsumerChannelFactory> multiplexChannelHandlers = new HashMap<>();
    private final Map<String, HttpServerBootstrapFactory> bootstrapFactories = new HashMap<>();
    @Metadata(label = "advanced")
    private NettyHttpBinding nettyHttpBinding;
    @Metadata(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @Metadata(label = "security")
    private NettyHttpSecurityConfiguration securityConfiguration;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public NettyHttpComponent() {
        // use the http configuration and filter strategy
        super(NettyHttpEndpoint.class);
        setConfiguration(new NettyHttpConfiguration());
        setHeaderFilterStrategy(new NettyHttpHeaderFilterStrategy());
        // use the binding that supports Rest DSL
        setNettyHttpBinding(new RestNettyHttpBinding(getHeaderFilterStrategy()));
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyHttpConfiguration config;
        if (getConfiguration() != null) {
            config = getConfiguration().copy();
        } else {
            config = new NettyHttpConfiguration();
        }

        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // merge any custom bootstrap configuration on the config
        NettyServerBootstrapConfiguration bootstrapConfiguration = resolveAndRemoveReferenceParameter(parameters, "bootstrapConfiguration", NettyServerBootstrapConfiguration.class);
        if (bootstrapConfiguration != null) {
            Map<String, Object> options = new HashMap<>();
            BeanIntrospection beanIntrospection = getCamelContext().adapt(ExtendedCamelContext.class).getBeanIntrospection();
            if (beanIntrospection.getProperties(bootstrapConfiguration, options, null, false)) {
                PropertyBindingSupport.bindProperties(getCamelContext(), config, options);
            }
        }

        // any custom security configuration
        NettyHttpSecurityConfiguration securityConfiguration = resolveAndRemoveReferenceParameter(parameters, "securityConfiguration", NettyHttpSecurityConfiguration.class);
        Map<String, Object> securityOptions = PropertiesHelper.extractProperties(parameters, "securityConfiguration.");

        NettyHttpBinding bindingFromUri = resolveAndRemoveReferenceParameter(parameters, "nettyHttpBinding", NettyHttpBinding.class);

        // are we using a shared http server?
        int sharedPort = -1;
        NettySharedHttpServer shared = resolveAndRemoveReferenceParameter(parameters, "nettySharedHttpServer", NettySharedHttpServer.class);
        if (shared != null) {
            // use port number from the shared http server
            LOG.debug("Using NettySharedHttpServer: {} with port: {}", shared, shared.getPort());
            sharedPort = shared.getPort();
        }

        // we must include the protocol in the remaining
        boolean hasProtocol = remaining != null && (remaining.startsWith("http://") || remaining.startsWith("http:")
                || remaining.startsWith("https://") || remaining.startsWith("https:")
                || remaining.startsWith("proxy://") || remaining.startsWith("proxy:"));
        if (!hasProtocol) {
            // http is the default protocol
            remaining = "http://" + remaining;
        }
        boolean hasSlash = remaining.startsWith("http://") || remaining.startsWith("https://") || remaining.startsWith("proxy://");
        if (!hasSlash) {
            // must have double slash after protocol
            if (remaining.startsWith("http:")) {
                remaining = "http://" + remaining.substring(5);
            } else {
                remaining = "https://" + remaining.substring(6);
            }
        }
        LOG.debug("Netty http url: {}", remaining);

        // set port on configuration which is either shared or using default values
        if (sharedPort != -1) {
            config.setPort(sharedPort);
        } else if (config.getPort() == -1 || config.getPort() == 0) {
            if (remaining.startsWith("http:")) {
                config.setPort(80);
            } else if (remaining.startsWith("https:")) {
                config.setPort(443);
            } else if (remaining.startsWith("proxy:")) {
                config.setPort(3128); // homage to Squid proxy
            }
        }
        if (config.getPort() == -1) {
            throw new IllegalArgumentException("Port number must be configured");
        }

        // configure configuration
        config = parseConfiguration(config, remaining, parameters);

        // set default ssl config
        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // validate config
        config.validateConfiguration();

        // create the address uri which includes the remainder parameters (which
        // is not configuration parameters for this component)
        URI u = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));

        String addressUri = URISupport.createRemainingURI(u, parameters).toString();

        NettyHttpEndpoint answer = new NettyHttpEndpoint(addressUri, this, config);
        setProperties(answer, parameters);

        // must use a copy of the binding on the endpoint to avoid sharing same
        // instance that can cause side-effects
        if (answer.getNettyHttpBinding() == null) {
            Object binding;
            if (bindingFromUri != null) {
                binding = bindingFromUri;
            } else {
                binding = getNettyHttpBinding();
            }
            if (binding instanceof RestNettyHttpBinding) {
                NettyHttpBinding copy = ((RestNettyHttpBinding) binding).copy();
                answer.setNettyHttpBinding(copy);
            } else if (binding instanceof DefaultNettyHttpBinding) {
                NettyHttpBinding copy = ((DefaultNettyHttpBinding) binding).copy();
                answer.setNettyHttpBinding(copy);
            }
        }
        if (headerFilterStrategy != null) {
            answer.setHeaderFilterStrategy(headerFilterStrategy);
        } else if (answer.getHeaderFilterStrategy() == null) {
            answer.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        if (securityConfiguration != null) {
            answer.setSecurityConfiguration(securityConfiguration);
        } else if (answer.getSecurityConfiguration() == null) {
            answer.setSecurityConfiguration(getSecurityConfiguration());
        }

        // configure any security options
        if (securityOptions != null && !securityOptions.isEmpty()) {
            securityConfiguration = answer.getSecurityConfiguration();
            if (securityConfiguration == null) {
                securityConfiguration = new NettyHttpSecurityConfiguration();
                answer.setSecurityConfiguration(securityConfiguration);
            }
            setProperties(securityConfiguration, securityOptions);
            validateParameters(uri, securityOptions, null);
        }

        answer.setNettySharedHttpServer(shared);
        return answer;
    }

    @Override
    protected NettyHttpConfiguration parseConfiguration(NettyConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        // ensure uri is encoded to be valid
        String safe = UnsafeUriCharactersEncoder.encodeHttpURI(remaining);
        URI uri = new URI(safe);
        configuration.parseURI(uri, parameters, this, "http", "https", "proxy");

        // force using tcp as the underlying transport
        configuration.setProtocol("tcp");
        configuration.setTextline(false);

        if (configuration instanceof NettyHttpConfiguration) {
            final NettyHttpConfiguration httpConfiguration = (NettyHttpConfiguration) configuration;

            httpConfiguration.setPath(uri.getPath());

            return httpConfiguration;
        }

        throw new IllegalStateException("Received NettyConfiguration instead of expected NettyHttpConfiguration, this is not supported.");
    }

    public NettyHttpBinding getNettyHttpBinding() {
        return nettyHttpBinding;
    }

    /**
     * To use a custom org.apache.camel.component.netty.http.NettyHttpBinding for binding to/from Netty and Camel Message API.
     */
    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }

    @Override
    public NettyHttpConfiguration getConfiguration() {
        return (NettyHttpConfiguration) super.getConfiguration();
    }

    public void setConfiguration(NettyHttpConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter headers.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public NettyHttpSecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

    /**
     * Refers to a org.apache.camel.component.netty.http.NettyHttpSecurityConfiguration for configuring secure web resources.
     */
    public void setSecurityConfiguration(NettyHttpSecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public synchronized HttpServerConsumerChannelFactory getMultiplexChannelHandler(int port) {
        HttpServerConsumerChannelFactory answer = multiplexChannelHandlers.get(port);
        if (answer == null) {
            answer = new HttpServerMultiplexChannelHandler();
            answer.init(port);
            multiplexChannelHandlers.put(port, answer);
        }
        return answer;
    }

    protected synchronized HttpServerBootstrapFactory getOrCreateHttpNettyServerBootstrapFactory(NettyHttpConsumer consumer) {
        String key = consumer.getConfiguration().getAddress();
        HttpServerBootstrapFactory answer = bootstrapFactories.get(key);
        if (answer == null) {
            HttpServerConsumerChannelFactory channelFactory = getMultiplexChannelHandler(consumer.getConfiguration().getPort());
            answer = new HttpServerBootstrapFactory(channelFactory);
            answer.init(getCamelContext(), consumer.getConfiguration(), new HttpServerInitializerFactory(consumer));
            bootstrapFactories.put(key, answer);
        }
        return answer;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration, parameters, false);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                              String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api) throws Exception {

        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);

        String scheme = "http";
        String host = "";
        int port = 0;

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = configuration;
        if (config == null) {
            config = CamelContextHelper.getRestConfiguration(getCamelContext(), "netty-http");
        }
        if (config.getScheme() != null) {
            scheme = config.getScheme();
        }
        if (config.getHost() != null) {
            host = config.getHost();
        }
        int num = config.getPort();
        if (num > 0) {
            port = num;
        }

        // prefix path with context-path if configured in rest-dsl configuration
        String contextPath = config.getContextPath();
        if (ObjectHelper.isNotEmpty(contextPath)) {
            contextPath = FileUtil.stripTrailingSeparator(contextPath);
            contextPath = FileUtil.stripLeadingSeparator(contextPath);
            if (ObjectHelper.isNotEmpty(contextPath)) {
                path = contextPath + "/" + path;
            }
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            host = RestComponentHelper.resolveRestHostName(host, config);
        }

        Map<String, Object> map = RestComponentHelper.initRestEndpointProperties("netty-http", config);

        // allow HTTP Options as we want to handle CORS in rest-dsl
        boolean cors = config.isEnableCORS();

        if (api) {
            map.put("matchOnUriPrefix", "true");
        }

        RestComponentHelper.addHttpRestrictParam(map, verb, cors);

        String url = RestComponentHelper.createRestConsumerUrl("netty-http", scheme, host, port, path, map);

        NettyHttpEndpoint endpoint = camelContext.getEndpoint(url, NettyHttpEndpoint.class);
        setProperties(endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String url = "netty-http:" + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), "netty-http");

        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("netty-http")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        if (host.startsWith("https:")) {
            map.put("ssl", true);
        }

        // get the endpoint
        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<String, Object>();

        // there are cases where we might end up here without component being created beforehand
        // we need to abide by the component properties specified in the parameters when creating
        // the component
        RestProducerFactoryHelper.setupComponentFor(url, camelContext, (Map<String, Object>) parameters.remove("component"));

        NettyHttpEndpoint endpoint = camelContext.getEndpoint(url, NettyHttpEndpoint.class);
        setProperties(endpoint, parameters);
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.setHeaderFilterStrategy(new NettyHttpRestHeaderFilterStrategy(path, queryParameters));

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), "netty-http");

        // configure additional options on netty-http configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(this, config.getComponentProperties());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(bootstrapFactories.values());
        bootstrapFactories.clear();

        ServiceHelper.stopService(multiplexChannelHandlers.values());
        multiplexChannelHandlers.clear();
    }
}
