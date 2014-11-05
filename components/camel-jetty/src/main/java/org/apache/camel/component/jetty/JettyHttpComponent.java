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
package org.apache.camel.component.jetty;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.management.MBeanServer;
import javax.servlet.Filter;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.UrlRewrite;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.MultiPartFilter;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An HttpComponent which starts an embedded Jetty for to handle consuming from
 * the http endpoints.
 *
 * @version 
 */
public class JettyHttpComponent extends HttpComponent implements RestConsumerFactory {
    public static final String TMP_DIR = "CamelJettyTempDir";
    
    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();
   
    private static final Logger LOG = LoggerFactory.getLogger(JettyHttpComponent.class);
    private static final String JETTY_SSL_KEYSTORE = "org.eclipse.jetty.ssl.keystore";
    private static final String JETTY_SSL_KEYPASSWORD = "org.eclipse.jetty.ssl.keypassword";
    private static final String JETTY_SSL_PASSWORD = "org.eclipse.jetty.ssl.password";

    protected String sslKeyPassword;
    protected String sslPassword;
    protected String sslKeystore;
    protected Map<Integer, SslSelectChannelConnector> sslSocketConnectors;
    protected Map<Integer, SelectChannelConnector> socketConnectors;
    protected Map<String, Object> sslSocketConnectorProperties;
    protected Map<String, Object> socketConnectorProperties;
    protected Integer httpClientMinThreads;
    protected Integer httpClientMaxThreads;
    protected Integer minThreads;
    protected Integer maxThreads;
    protected ThreadPool threadPool;
    protected MBeanContainer mbContainer;
    protected boolean enableJmx;
    protected JettyHttpBinding jettyHttpBinding;
    protected Long continuationTimeout;
    protected boolean useContinuation = true;
    protected SSLContextParameters sslContextParameters;
    protected Integer requestBufferSize;
    protected Integer requestHeaderSize;
    protected Integer responseBufferSize;
    protected Integer responseHeaderSize;
    protected String proxyHost;
    private Integer proxyPort;

    public JettyHttpComponent() {
        super(JettyHttpEndpoint.class);
    }

    class ConnectorRef {
        Server server;
        Connector connector;
        CamelServlet servlet;
        int refCount;

        public ConnectorRef(Server server, Connector connector, CamelServlet servlet) {
            this.server = server;
            this.connector = connector;
            this.servlet = servlet;
            increment();
        }

        public int increment() {
            return ++refCount;
        }

        public int decrement() {
            return --refCount;
        }
        
        public int getRefCount() {
            return refCount;
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // must extract well known parameters before we create the endpoint
        List<Handler> handlerList = resolveAndRemoveReferenceListParameter(parameters, "handlers", Handler.class);
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        JettyHttpBinding jettyBinding = resolveAndRemoveReferenceParameter(parameters, "jettyHttpBindingRef", JettyHttpBinding.class);
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);
        Boolean transferException = getAndRemoveParameter(parameters, "transferException", Boolean.class);
        Boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class);
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        Boolean enableJmx = getAndRemoveParameter(parameters, "enableJmx", Boolean.class);
        Boolean enableMultipartFilter = getAndRemoveParameter(parameters, "enableMultipartFilter",
                                                              Boolean.class, true);
        Filter multipartFilter = resolveAndRemoveReferenceParameter(parameters, "multipartFilterRef", Filter.class);
        List<Filter> filters = resolveAndRemoveReferenceListParameter(parameters, "filtersRef", Filter.class);
        Long continuationTimeout = getAndRemoveParameter(parameters, "continuationTimeout", Long.class);
        Boolean useContinuation = getAndRemoveParameter(parameters, "useContinuation", Boolean.class);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        UrlRewrite urlRewrite = resolveAndRemoveReferenceParameter(parameters, "urlRewrite", UrlRewrite.class);
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParametersRef", SSLContextParameters.class);
        SSLContextParameters ssl = sslContextParameters != null ? sslContextParameters : this.sslContextParameters;
        String proxyHost = getAndRemoveParameter(parameters, "proxyHost", String.class, getProxyHost());
        Integer proxyPort = getAndRemoveParameter(parameters, "proxyPort", Integer.class, getProxyPort());
        Integer responseBufferSize = getAndRemoveParameter(parameters, "responseBufferSize", Integer.class, getResponseBufferSize());
        Integer httpClientMinThreads = getAndRemoveParameter(parameters, "httpClientMinThreads", Integer.class, this.httpClientMinThreads);
        Integer httpClientMaxThreads = getAndRemoveParameter(parameters, "httpClientMaxThreads", Integer.class, this.httpClientMaxThreads);

        // extract httpClient. parameters
        Map<String, Object> httpClientParameters = IntrospectionSupport.extractProperties(parameters, "httpClient.");

        String address = remaining;
        URI addressUri = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(address));
        URI endpointUri = URISupport.createRemainingURI(addressUri, parameters);
        // need to keep the httpMethodRestrict parameter for the endpointUri
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(addressUri, parameters);
        // create endpoint after all known parameters have been extracted from parameters
        JettyHttpEndpoint endpoint = new JettyHttpEndpoint(this, endpointUri.toString(), httpUri);
        
        
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            setEndpointHeaderFilterStrategy(endpoint);
        }
        if (proxyHost != null) {
            endpoint.setProxyHost(proxyHost);
            endpoint.setProxyPort(proxyPort);
        }
        if (urlRewrite != null) {
            // let CamelContext deal with the lifecycle of the url rewrite
            // this ensures its being shutdown when Camel shutdown etc.
            getCamelContext().addService(urlRewrite);
            endpoint.setUrlRewrite(urlRewrite);
        }
        // setup the proxy host and proxy port
        

        if (httpClientParameters != null && !httpClientParameters.isEmpty()) {
            endpoint.setHttpClientParameters(httpClientParameters);
        }
        if (handlerList.size() > 0) {
            endpoint.setHandlers(handlerList);
        }
        // prefer to use endpoint configured over component configured
        if (binding == null) {
            // fallback to component configured
            binding = getHttpBinding();
        }
        if (binding != null) {
            endpoint.setBinding(binding);
        }
        // prefer to use endpoint configured over component configured
        if (jettyBinding == null) {
            // fallback to component configured
            jettyBinding = getJettyHttpBinding();
        }
        if (jettyBinding != null) {
            endpoint.setJettyBinding(jettyBinding);
        }
        // should we use an exception for failed error codes?
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        // should we transfer exception as serialized object
        if (transferException != null) {
            endpoint.setTransferException(transferException);
        }
        if (bridgeEndpoint != null) {
            endpoint.setBridgeEndpoint(bridgeEndpoint);
        }
        if (matchOnUriPrefix != null) {
            endpoint.setMatchOnUriPrefix(matchOnUriPrefix);
        }
        if (enableJmx != null) {
            endpoint.setEnableJmx(enableJmx);
        } else { 
            // set this option based on setting of JettyHttpComponent
            endpoint.setEnableJmx(isEnableJmx());
        }
        
        endpoint.setEnableMultipartFilter(enableMultipartFilter);
        if (multipartFilter != null) {
            endpoint.setMultipartFilter(multipartFilter);
            endpoint.setEnableMultipartFilter(true);
        }
        if (filters != null) {
            endpoint.setFilters(filters);
        }

        if (continuationTimeout != null) {
            endpoint.setContinuationTimeout(continuationTimeout);
        }
        if (useContinuation != null) {
            endpoint.setUseContinuation(useContinuation);
        }
        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }
        if (ssl != null) {
            endpoint.setSslContextParameters(ssl);
        }
        if (responseBufferSize != null) {
            endpoint.setResponseBufferSize(responseBufferSize);
        }
        if (httpClientMinThreads != null) {
            endpoint.setHttpClientMinThreads(httpClientMinThreads);
        }
        if (httpClientMaxThreads != null) {
            endpoint.setHttpClientMaxThreads(httpClientMaxThreads);
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        // Make sure that there is a connector for the requested endpoint.
        JettyHttpEndpoint endpoint = (JettyHttpEndpoint)consumer.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if ("https".equals(endpoint.getProtocol())) {
                    connector = getSslSocketConnector(endpoint);
                } else {
                    connector = getSocketConnector(endpoint.getPort());
                }
                connector.setPort(endpoint.getPort());
                connector.setHost(endpoint.getHttpUri().getHost());
                if ("localhost".equalsIgnoreCase(endpoint.getHttpUri().getHost())) {
                    LOG.warn("You use localhost interface! It means that no external connections will be available."
                            + " Don't you want to use 0.0.0.0 instead (all network interfaces)? " + endpoint);
                }
                Server server = createServer();
                if (endpoint.isEnableJmx()) {
                    enableJmx(server);
                }
                // This setting is only work for the first endpoint which create the server
                // just set if we need sendServerVersion, the default value is true
                server.setSendServerVersion(endpoint.isSendServerVersion());
                server.addConnector(connector);
                server.setSendDateHeader(endpoint.isSendDateHeader());

                connectorRef = new ConnectorRef(server, connector, createServletForConnector(server, connector, endpoint.getHandlers(), endpoint));
                // must enable session before we start
                if (endpoint.isSessionSupport()) {
                    enableSessionSupport(connectorRef.server, connectorKey);
                }
                connectorRef.server.start();
                
                CONNECTORS.put(connectorKey, connectorRef);
                
            } else {
                
                if (endpoint.getHandlers() != null && !endpoint.getHandlers().isEmpty()) {
                    // As the server is started, we need to stop the server for a while to add the new handler
                    connectorRef.server.stop();
                    addJettyHandlers(connectorRef.server, endpoint.getHandlers());
                    connectorRef.server.start();
                }
                // ref track the connector
                connectorRef.increment();
            }
            // check the session support
            if (endpoint.isSessionSupport()) {
                enableSessionSupport(connectorRef.server, connectorKey);
            }
            
            if (endpoint.isEnableMultipartFilter()) {
                enableMultipartFilter(endpoint, connectorRef.server, connectorKey);
            }
            
            if (endpoint.getFilters() != null && endpoint.getFilters().size() > 0) {
                setFilters(endpoint, connectorRef.server, connectorKey);
            }
            connectorRef.servlet.connect(consumer);
        }
    }
    

    private void enableJmx(Server server) {
        MBeanContainer containerToRegister = getMbContainer();
        if (containerToRegister != null) {
            LOG.info("Jetty JMX Extensions is enabled");
            server.getContainer().addEventListener(containerToRegister);
            // Since we may have many Servers running, don't tie the MBeanContainer
            // to a Server lifecycle or we end up closing it while it is still in use.
            //server.addBean(mbContainer);
        }
    }

    private void enableSessionSupport(Server server, String connectorKey) throws Exception {
        ServletContextHandler context = server.getChildHandlerByClass(ServletContextHandler.class);
        if (context.getSessionHandler() == null) {
            SessionHandler sessionHandler = new SessionHandler();
            if (context.isStarted()) {
                throw new IllegalStateException("Server has already been started. Cannot enabled sessionSupport on " + connectorKey);
            } else {
                context.setSessionHandler(sessionHandler);
            }
        }
    }
    
    private void setFilters(JettyHttpEndpoint endpoint, Server server, String connectorKey) {
        ServletContextHandler context = server.getChildHandlerByClass(ServletContextHandler.class);
        List<Filter> filters = endpoint.getFilters();
        for (Filter filter : filters) {
            FilterHolder filterHolder = new FilterHolder();
            filterHolder.setFilter(new CamelFilterWrapper(filter));
            String pathSpec = endpoint.getPath();
            if (pathSpec == null || "".equals(pathSpec)) {
                pathSpec = "/";
            }
            if (endpoint.isMatchOnUriPrefix()) {
                pathSpec = pathSpec.endsWith("/") ? pathSpec + "*" : pathSpec + "/*";
            }
            addFilter(context, filterHolder, pathSpec);
        }
    }
    
    private void addFilter(ServletContextHandler context, FilterHolder filterHolder, String pathSpec) {
        context.getServletHandler().addFilterWithMapping(filterHolder, pathSpec, 0);
    }

    private void enableMultipartFilter(HttpEndpoint endpoint, Server server, String connectorKey) throws Exception {
        ServletContextHandler context = server.getChildHandlerByClass(ServletContextHandler.class);
        CamelContext camelContext = this.getCamelContext();
        FilterHolder filterHolder = new FilterHolder();
        filterHolder.setInitParameter("deleteFiles", "true");
        if (ObjectHelper.isNotEmpty(camelContext.getProperty(TMP_DIR))) {
            File file = new File(camelContext.getProperty(TMP_DIR));
            if (!file.isDirectory()) {
                throw new RuntimeCamelException(
                        "The temp file directory of camel-jetty is not exists, please recheck it with directory name :"
                                + camelContext.getProperties().get(TMP_DIR));
            }
            context.setAttribute("javax.servlet.context.tempdir", file);
        }
        // if a filter ref was provided, use it.
        Filter filter = ((JettyHttpEndpoint) endpoint).getMultipartFilter();
        if (filter == null) {
            // if no filter ref was provided, use the default filter
            filter = new MultiPartFilter();
        }
        filterHolder.setFilter(new CamelFilterWrapper(filter));
        String pathSpec = endpoint.getPath();
        if (pathSpec == null || "".equals(pathSpec)) {
            pathSpec = "/";
        }
        if (endpoint.isMatchOnUriPrefix()) {
            pathSpec = pathSpec.endsWith("/") ? pathSpec + "*" : pathSpec + "/*";
        }
        addFilter(context, filterHolder, pathSpec);
        LOG.debug("using multipart filter implementation " + filter.getClass().getName() + " for path " + pathSpec);
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     */
    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        // If the connector is not needed anymore then stop it
        HttpEndpoint endpoint = consumer.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);
        
        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef != null) {
                connectorRef.servlet.disconnect(consumer);
                if (connectorRef.decrement() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    CONNECTORS.remove(connectorKey);
                    // Camel controls the lifecycle of these entities so remove the
                    // registered MBeans when Camel is done with the managed objects.
                    if (mbContainer != null) {
                        mbContainer.removeBean(connectorRef.server);
                        mbContainer.removeBean(connectorRef.connector);
                    }
                }
            }
        }
    }
    
    private String getConnectorKey(HttpEndpoint endpoint) {
        return endpoint.getProtocol() + ":" + endpoint.getHttpUri().getHost() + ":" + endpoint.getPort();
    }

    // Properties
    // -------------------------------------------------------------------------
       
    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslPassword() {
        return sslPassword;
    }

    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }

    public void setKeystore(String sslKeystore) {
        this.sslKeystore = sslKeystore;
    }

    public String getKeystore() {
        return sslKeystore;
    }

    protected Connector getSslSocketConnector(JettyHttpEndpoint endpoint) throws Exception {
        Connector answer = null;
        if (sslSocketConnectors != null) {
            answer = sslSocketConnectors.get(endpoint.getPort());
        }
        if (answer == null) {
            answer = createSslSocketConnector(endpoint);
        }
        return answer;
    }
    
    protected Connector createSslSocketConnector(JettyHttpEndpoint endpoint) throws Exception {
        SslSelectChannelConnector answer = null;
        
        // Note that this was set on the endpoint when it was constructed.  It was
        // either explicitly set at the component or on the endpoint, but either way,
        // the value is already set.  We therefore do not need to look at the component
        // level SSLContextParameters again in this method.
        SSLContextParameters endpointSslContextParameters = endpoint.getSslContextParameters();
        
        if (endpointSslContextParameters != null) {
            SslContextFactory contextFact = createSslContextFactory(endpointSslContextParameters);
            for (Constructor<?> c : SslSelectChannelConnector.class.getConstructors()) {
                if (c.getParameterTypes().length == 1
                    && c.getParameterTypes()[0].isInstance(contextFact)) {
                    answer = (SslSelectChannelConnector)c.newInstance(contextFact);
                }
            }
        } else {
            answer = new SslSelectChannelConnector();
            // with default null values, jetty ssl system properties
            // and console will be read by jetty implementation
    
            String keystoreProperty = System.getProperty(JETTY_SSL_KEYSTORE);
            if (keystoreProperty != null) {
                setKeyStorePath(answer, keystoreProperty);
            } else if (sslKeystore != null) {
                setKeyStorePath(answer, sslKeystore);
            }
    
            String keystorePassword = System.getProperty(JETTY_SSL_KEYPASSWORD);
            if (keystorePassword != null) {
                setKeyManagerPassword(answer, keystorePassword);
            } else if (sslKeyPassword != null) {
                setKeyManagerPassword(answer, sslKeyPassword);
            }
    
            String password = System.getProperty(JETTY_SSL_PASSWORD);
            if (password != null) {
                setKeyStorePassword(answer, password);
            } else if (sslPassword != null) {
                setKeyStorePassword(answer, sslPassword);
            }
        }
        
        if (getSslSocketConnectorProperties() != null) {
            if (endpointSslContextParameters != null) {
                LOG.warn("An SSLContextParameters instance is configured "
                         + "in addition to SslSocketConnectorProperties.  Any SslSocketConnector properties"
                         + "related to the SSLContext will be ignored in favor of the settings provided through"
                         + "SSLContextParameters.");
            }
            
            // must copy the map otherwise it will be deleted
            Map<String, Object> properties = new HashMap<String, Object>(getSslSocketConnectorProperties());
            IntrospectionSupport.setProperties(answer, properties);
            if (properties.size() > 0) {
                throw new IllegalArgumentException("There are " + properties.size()
                    + " parameters that couldn't be set on the SslSocketConnector."
                    + " Check the uri if the parameters are spelt correctly and that they are properties of the SslSocketConnector."
                    + " Unknown parameters=[" + properties + "]");
            }
        }

        if (answer != null && requestBufferSize != null) {
            answer.setRequestBufferSize(requestBufferSize);
        }
        if (answer != null && requestHeaderSize != null) {
            answer.setRequestHeaderSize(requestHeaderSize);
        }
        if (answer != null && responseBufferSize != null) {
            answer.setResponseBufferSize(responseBufferSize);
        }
        if (answer != null && responseHeaderSize != null) {
            answer.setResponseBufferSize(responseHeaderSize);
        }
        return answer;
    }
    
    private SslContextFactory createSslContextFactory(SSLContextParameters ssl) throws GeneralSecurityException, IOException {
        SslContextFactory answer = new SslContextFactory() {

            // This method is for Jetty 7.0.x ~ 7.4.x
            @SuppressWarnings("unused")
            public boolean checkConfig() {
                if (getSslContext() == null) {
                    return checkSSLContextFactoryConfig(this);
                } else {
                    return true;
                }
            }
            // This method is for Jetty 7.5.x
            public void checkKeyStore() {
                // here we don't check the SslContext as it is already created
            }
            
        };
        answer.setSslContext(ssl.createSSLContext());
        return answer;
    }
    
    private void invokeSslContextFactoryMethod(Object connector, String method, String value) {
        Object factory;
        try {
            factory = connector.getClass().getMethod("getSslContextFactory").invoke(connector);
        } catch (Exception e) {
            throw new RuntimeCamelException("Error invoking method getSslContextFactory on " + connector, e);
        }
        try {
            factory.getClass().getMethod(method, String.class).invoke(factory, value);
        } catch (Exception e) {
            throw new RuntimeCamelException("Error invoking method " + method + " on " + factory, e);
        }
    }
        
    private void setKeyStorePassword(SslSelectChannelConnector answer, String password) {
        invokeSslContextFactoryMethod(answer, "setKeyStorePassword", password);
    }

    private void setKeyManagerPassword(SslSelectChannelConnector answer, String keystorePassword) {
        invokeSslContextFactoryMethod(answer, "setKeyManagerPassword", keystorePassword);
    }

    private void setKeyStorePath(SslSelectChannelConnector answer, String keystoreProperty) {
        invokeSslContextFactoryMethod(answer, "setKeyStorePath", keystoreProperty);
    }

    protected boolean checkSSLContextFactoryConfig(Object instance) {
        try {
            Method method = instance.getClass().getMethod("checkConfig");
            return (Boolean)method.invoke(instance);
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (IllegalArgumentException e) {
            // ignore
        } catch (IllegalAccessException e) {
            // ignore
        } catch (InvocationTargetException e) {
            // ignore
        }
        return false;
    }

    public Map<Integer, SslSelectChannelConnector> getSslSocketConnectors() {
        return sslSocketConnectors;
    }

    public void setSslSocketConnectors(Map <Integer, SslSelectChannelConnector> connectors) {
        sslSocketConnectors = connectors;
    }

    public Connector getSocketConnector(int port) throws Exception {
        Connector answer = null;
        if (socketConnectors != null) {
            answer = socketConnectors.get(port);
        }
        if (answer == null) {
            answer = createSocketConnector();
        }
        return answer;
    }

    protected Connector createSocketConnector() throws Exception {
        SelectChannelConnector answer = new SelectChannelConnector();
        if (getSocketConnectorProperties() != null) {
            // must copy the map otherwise it will be deleted
            Map<String, Object> properties = new HashMap<String, Object>(getSocketConnectorProperties());
            IntrospectionSupport.setProperties(answer, properties);
            if (properties.size() > 0) {
                throw new IllegalArgumentException("There are " + properties.size()
                    + " parameters that couldn't be set on the SocketConnector."
                    + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                    + " Unknown parameters=[" + properties + "]");
            }
        }

        if (requestBufferSize != null) {
            answer.setRequestBufferSize(requestBufferSize);
        }
        if (requestHeaderSize != null) {
            answer.setRequestHeaderSize(requestHeaderSize);
        }
        if (responseBufferSize != null) {
            answer.setResponseBufferSize(responseBufferSize);
        }
        if (responseHeaderSize != null) {
            answer.setResponseBufferSize(responseHeaderSize);
        }
        return answer;
    }

    public void setSocketConnectors(Map<Integer, SelectChannelConnector> socketConnectors) {
        this.socketConnectors = socketConnectors;
    }

    /**
     * Creates a new {@link HttpClient} and configures its proxy/thread pool and SSL based on this
     * component settings.
     *
     * @Param endpoint   the instance of JettyHttpEndpoint
     * @param minThreads optional minimum number of threads in client thread pool
     * @param maxThreads optional maximum number of threads in client thread pool
     * @param ssl        option SSL parameters
     */
    public CamelHttpClient createHttpClient(JettyHttpEndpoint endpoint, Integer minThreads, Integer maxThreads, SSLContextParameters ssl) throws Exception {
        CamelHttpClient httpClient = null;
        if (ssl != null) {
            httpClient = new CamelHttpClient(createSslContextFactory(ssl));
        } else {
            httpClient = new CamelHttpClient();
        }
        httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        
        CamelContext context = endpoint.getCamelContext();

        if (context != null 
            && ObjectHelper.isNotEmpty(context.getProperty("http.proxyHost"))
            && ObjectHelper.isNotEmpty(context.getProperty("http.proxyPort"))) {
            String host = context.getProperty("http.proxyHost");
            int port = Integer.parseInt(context.getProperty("http.proxyPort"));
            LOG.debug("CamelContext properties http.proxyHost and http.proxyPort detected. Using http proxy host: {} port: {}", host, port);
            httpClient.setProxy(new Address(host, port));
        }

        if (ObjectHelper.isNotEmpty(endpoint.getProxyHost()) && endpoint.getProxyPort() > 0) {
            String host = endpoint.getProxyHost();
            int port = endpoint.getProxyPort();
            LOG.debug("proxyHost and proxyPort options detected. Using http proxy host: {} port: {}", host, port);
            httpClient.setProxy(new Address(host, port));
        }
        
        // must have both min and max
        if (minThreads != null || maxThreads != null) {

            // must have both options
            if (minThreads == null || maxThreads == null) {
                throw new IllegalArgumentException("Both min and max thread pool sizes must be provided.");
            }

            // use QueueThreadPool as the default bounded is deprecated (see SMXCOMP-157)
            QueuedThreadPool qtp = new QueuedThreadPool();
            qtp.setMinThreads(minThreads.intValue());
            qtp.setMaxThreads(maxThreads.intValue());
            // and we want to use daemon threads
            qtp.setDaemon(true);
            // let the thread names indicate they are from the client
            qtp.setName("CamelJettyClient(" + ObjectHelper.getIdentityHashCode(httpClient) + ")");
            httpClient.setThreadPool(qtp);
        }
        
        if (LOG.isDebugEnabled()) {
            if (minThreads != null) {
                LOG.debug("Created HttpClient with thread pool {}-{} -> {}", new Object[]{minThreads, maxThreads, httpClient});
            } else {
                LOG.debug("Created HttpClient with default thread pool size -> {}", httpClient);
            }
        }
        
        return httpClient;
    }

    public Integer getHttpClientMinThreads() {
        return httpClientMinThreads;
    }

    public void setHttpClientMinThreads(Integer httpClientMinThreads) {
        this.httpClientMinThreads = httpClientMinThreads;
    }

    public Integer getHttpClientMaxThreads() {
        return httpClientMaxThreads;
    }

    public void setHttpClientMaxThreads(Integer httpClientMaxThreads) {
        this.httpClientMaxThreads = httpClientMaxThreads;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public boolean isEnableJmx() {
        return enableJmx;
    }
    
    public JettyHttpBinding getJettyHttpBinding() {
        return jettyHttpBinding;
    }

    public void setJettyHttpBinding(JettyHttpBinding jettyHttpBinding) {
        this.jettyHttpBinding = jettyHttpBinding;
    }

    public synchronized MBeanContainer getMbContainer() {
        // If null, provide the default implementation.
        if (mbContainer == null) {
            MBeanServer mbs = null;
            
            final ManagementStrategy mStrategy = this.getCamelContext().getManagementStrategy();
            final ManagementAgent mAgent = mStrategy.getManagementAgent();
            if (mAgent != null) {
                mbs = mAgent.getMBeanServer();
            }
            
            if (mbs != null) {
                mbContainer = new MBeanContainer(mbs);
                startMbContainer();
            } else {
                LOG.warn("JMX disabled in CamelContext. Jetty JMX extensions will remain disabled.");
            }
        }
        
        return this.mbContainer;
    }

    public void setMbContainer(MBeanContainer mbContainer) {
        this.mbContainer = mbContainer;
    }

    public Map<String, Object> getSslSocketConnectorProperties() {
        return sslSocketConnectorProperties;
    }

    public void setSslSocketConnectorProperties(Map<String, Object> sslSocketConnectorProperties) {
        this.sslSocketConnectorProperties = sslSocketConnectorProperties;
    }

    public Map<String, Object> getSocketConnectorProperties() {
        return socketConnectorProperties;
    }

    public void setSocketConnectorProperties(Map<String, Object> socketConnectorProperties) {
        this.socketConnectorProperties = socketConnectorProperties;
    }

    public void addSocketConnectorProperty(String key, Object value) {
        if (socketConnectorProperties == null) {
            socketConnectorProperties = new HashMap<String, Object>();
        }
        socketConnectorProperties.put(key, value);
    }

    public void addSslSocketConnectorProperty(String key, Object value) {
        if (sslSocketConnectorProperties == null) {
            sslSocketConnectorProperties = new HashMap<String, Object>();
        }
        sslSocketConnectorProperties.put(key, value);
    }

    public Long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public boolean isUseContinuation() {
        return useContinuation;
    }

    public void setUseContinuation(boolean useContinuation) {
        this.useContinuation = useContinuation;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Integer getResponseBufferSize() {
        return responseBufferSize;
    }

    public void setResponseBufferSize(Integer responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

    public Integer getRequestBufferSize() {
        return requestBufferSize;
    }

    public void setRequestBufferSize(Integer requestBufferSize) {
        this.requestBufferSize = requestBufferSize;
    }

    public Integer getRequestHeaderSize() {
        return requestHeaderSize;
    }

    public void setRequestHeaderSize(Integer requestHeaderSize) {
        this.requestHeaderSize = requestHeaderSize;
    }

    public Integer getResponseHeaderSize() {
        return responseHeaderSize;
    }

    public void setResponseHeaderSize(Integer responseHeaderSize) {
        this.responseHeaderSize = responseHeaderSize;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    // Implementation methods
    // -------------------------------------------------------------------------


    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

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
        RestConfiguration config = getCamelContext().getRestConfiguration();
        if (config.getComponent() == null || config.getComponent().equals("jetty")) {
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
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config != null && (config.getComponent() == null || config.getComponent().equals("jetty"))) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url = "jetty:%s://%s:%s/%s?httpMethodRestrict=%s";
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);
        // get the endpoint
        url = String.format(url, scheme, host, port, path, restrict);

        if (!query.isEmpty()) {
            url = url + "&" + query;
        }
        
        JettyHttpEndpoint endpoint = camelContext.getEndpoint(url, JettyHttpEndpoint.class);
        setProperties(endpoint, parameters);

        // disable this filter as we want to use ours
        endpoint.setEnableMultipartFilter(false);
        // use the rest binding
        endpoint.setBinding(new JettyRestHttpBinding());

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config != null && config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    protected CamelServlet createServletForConnector(Server server, Connector connector,
                                                     List<Handler> handlers, JettyHttpEndpoint endpoint) throws Exception {
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        context.setConnectorNames(new String[] {connector.getName()});

        addJettyHandlers(server, handlers);

        CamelServlet camelServlet;
        boolean jetty = endpoint.getUseContinuation() != null ? endpoint.getUseContinuation() : isUseContinuation();
        if (jetty) {
            // use Jetty continuations
            CamelContinuationServlet jettyServlet = new CamelContinuationServlet();
            // configure timeout and log it so end user know what we are using
            Long timeout = endpoint.getContinuationTimeout() != null ? endpoint.getContinuationTimeout() : getContinuationTimeout();
            if (timeout != null) {
                LOG.info("Using Jetty continuation timeout: " + timeout + " millis for: " + endpoint);
                jettyServlet.setContinuationTimeout(timeout);
            } else {
                LOG.info("Using default Jetty continuation timeout for: " + endpoint);
            }

            // use the jetty servlet
            camelServlet = jettyServlet;
        } else {
            // do not use jetty so use a plain servlet
            camelServlet = new CamelServlet();
            LOG.info("Jetty continuation is disabled for: " + endpoint);
        }

        ServletHolder holder = new ServletHolder();
        holder.setServlet(camelServlet);
        context.addServlet(holder, "/*");

        // use rest enabled resolver in case we use rest
        camelServlet.setServletResolveConsumerStrategy(new JettyRestServletResolveConsumerStrategy());

        return camelServlet;
    }
    
    protected void addJettyHandlers(Server server, List<Handler> handlers) {
        if (handlers != null && !handlers.isEmpty()) {
            for (Handler handler : handlers) {
                if (handler instanceof HandlerWrapper) {
                    ((HandlerWrapper) handler).setHandler(server.getHandler());
                    server.setHandler(handler);
                } else {
                    HandlerCollection handlerCollection = new HandlerCollection();
                    handlerCollection.addHandler(server.getHandler());
                    handlerCollection.addHandler(handler);
                    server.setHandler(handlerCollection);
                }
            }
        }
        
    }
    
    protected Server createServer() throws Exception {
        Server server = new Server();
        ContextHandlerCollection collection = new ContextHandlerCollection();
        server.setHandler(collection);

        // configure thread pool if min/max given
        if (minThreads != null || maxThreads != null) {
            if (getThreadPool() != null) {
                throw new IllegalArgumentException("You cannot configure both minThreads/maxThreads and a custom threadPool on JettyHttpComponent: " + this);
            }
            QueuedThreadPool qtp = new QueuedThreadPool();
            if (minThreads != null) {
                qtp.setMinThreads(minThreads.intValue());
            }
            if (maxThreads != null) {
                qtp.setMaxThreads(maxThreads.intValue());
            }
            // let the thread names indicate they are from the server
            qtp.setName("CamelJettyServer(" + ObjectHelper.getIdentityHashCode(server) + ")");
            try {
                qtp.start();
            } catch (Exception e) {
                throw new RuntimeCamelException("Error starting JettyServer thread pool: " + qtp, e);
            }
            server.setThreadPool(qtp);
        }

        if (getThreadPool() != null) {
            server.setThreadPool(getThreadPool());
        }

        return server;
    }
    
    /**
     * Starts {@link #mbContainer} and registers the container with itself as a managed bean
     * logging an error if there is a problem starting the container.
     * Does nothing if {@link #mbContainer} is {@code null}.
     */
    protected void startMbContainer() {
        if (mbContainer != null && !mbContainer.isStarted()) {
            try {
                mbContainer.start();
                // Publish the container itself for consistency with
                // traditional embedded Jetty configurations.
                mbContainer.addBean(mbContainer);
            } catch (Throwable e) {
                LOG.warn("Could not start Jetty MBeanContainer. Jetty JMX extensions will remain disabled.", e);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        startMbContainer();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (CONNECTORS.size() > 0) {
            for (String connectorKey : CONNECTORS.keySet()) {
                ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
                if (connectorRef != null && connectorRef.getRefCount() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    // Camel controls the lifecycle of these entities so remove the
                    // registered MBeans when Camel is done with the managed objects.
                    if (mbContainer != null) {
                        mbContainer.removeBean(connectorRef.server);
                        mbContainer.removeBean(connectorRef.connector);
                    }
                    CONNECTORS.remove(connectorKey);
                }
            }
        }
        if (mbContainer != null) {
            mbContainer.stop();
            mbContainer = null;
        }
    }
}
