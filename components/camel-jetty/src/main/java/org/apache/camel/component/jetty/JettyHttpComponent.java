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
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.servlet.Filter;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
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
import org.eclipse.jetty.util.component.LifeCycle;
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
public class JettyHttpComponent extends HttpComponent {
    public static final String TMP_DIR = "CamelJettyTempDir";
    
    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();
   
    private static final transient Logger LOG = LoggerFactory.getLogger(JettyHttpComponent.class);
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
    protected HttpClient httpClient;
    protected ThreadPool httpClientThreadPool;
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
    protected boolean isExplicitHttpClient;

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
        String addressUri = uri.startsWith("jetty:") ? remaining : uri;
        Map<String, Object> httpClientParameters = new HashMap<String, Object>(parameters);
        
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
        Long continuationTimeout = getAndRemoveParameter(parameters, "continuationTimeout", Long.class);
        Boolean useContinuation = getAndRemoveParameter(parameters, "useContinuation", Boolean.class);
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParametersRef", SSLContextParameters.class);
        
        
        // configure http client if we have url configuration for it
        // http client is only used for jetty http producer (hence not very commonly used)
        HttpClient client = null;
        if (IntrospectionSupport.hasProperties(parameters, "httpClient.") || sslContextParameters != null) {
            client = getNewHttpClient();
            
            if (IntrospectionSupport.hasProperties(parameters, "httpClient.")) {
                if (isExplicitHttpClient) {
                    LOG.warn("The user explicitly set an HttpClient instance on the component, "
                             + "but this endpoint provides HttpClient configuration.  Are you sure that "
                             + "this is what was intended?  Applying endpoint configuration to a new HttpClient instance "
                             + "to avoid altering existing HttpClient instances.");
                }
            
                // set additional parameters on http client
                IntrospectionSupport.setProperties(client, parameters, "httpClient.");
                // validate that we could resolve all httpClient. parameters as this component is lenient
                validateParameters(uri, parameters, "httpClient.");
            }
            
            // Note that the component level instance is already configured in getNewHttpClient.
            // We replace it here for endpoint level config.
            if (sslContextParameters != null) {
                if (isExplicitHttpClient) {
                    LOG.warn("The user explicitly set an HttpClient instance on the component, "
                             + "but this endpoint provides SSLContextParameters configuration.  Are you sure that "
                             + "this is what was intended?  Applying endpoint configuration to a new HttpClient instance "
                             + "to avoid altering existing HttpClient instances.");
                }
                
                ((CamelHttpClient) client).setSSLContext(sslContextParameters.createSSLContext());
            }
        } else {
            // Either we use the default one created by the component or we are using
            // one explicitly set by the end user, either way, we just use it as is.
            client = getHttpClient();
        }
        // keep the configure parameters for the http client
        for (String key : parameters.keySet()) {
            httpClientParameters.remove(key);
        }
        URI endpointUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(httpClientParameters));
        
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(parameters));
     
        // create endpoint after all known parameters have been extracted from parameters
        JettyHttpEndpoint endpoint = new JettyHttpEndpoint(this, endpointUri.toString(), httpUri);
        setEndpointHeaderFilterStrategy(endpoint);

        if (client != null) {
            endpoint.setClient(client);
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

        if (continuationTimeout != null) {
            endpoint.setContinuationTimeout(continuationTimeout);
        }
        if (useContinuation != null) {
            endpoint.setUseContinuation(useContinuation);
        }
        
        if (sslContextParameters == null) {
            sslContextParameters = this.sslContextParameters;
        }
        endpoint.setSslContextParameters(sslContextParameters);

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
                server.addConnector(connector);

                connectorRef = new ConnectorRef(server, connector, createServletForConnector(server, connector, endpoint.getHandlers(), endpoint));
                // must enable session before we start
                if (endpoint.isSessionSupport()) {
                    enableSessionSupport(connectorRef.server, connectorKey);
                }
                connectorRef.server.start();
                
                CONNECTORS.put(connectorKey, connectorRef);
                
            } else {
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
        ServletContextHandler context = (ServletContextHandler)server.getChildHandlerByClass(ServletContextHandler.class);
        if (context.getSessionHandler() == null) {
            SessionHandler sessionHandler = new SessionHandler();
            if (context.isStarted()) {
                throw new IllegalStateException("Server has already been started. Cannot enabled sessionSupport on " + connectorKey);
            } else {
                context.setSessionHandler(sessionHandler);
            }
        }
    }
    
    private void enableMultipartFilter(HttpEndpoint endpoint, Server server, String connectorKey) throws Exception {
        ServletContextHandler context = (ServletContextHandler) server
                .getChildHandlerByClass(ServletContextHandler.class);
        CamelContext camelContext = this.getCamelContext();
        FilterHolder filterHolder = new FilterHolder();
        filterHolder.setInitParameter("deleteFiles", "true");
        if (ObjectHelper.isNotEmpty(camelContext.getProperties().get(TMP_DIR))) {
            File file = new File(camelContext.getProperties().get(TMP_DIR));
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
        filterHolder.setFilter(new CamelMultipartFilter(filter));
        String pathSpec = endpoint.getPath();
        if (pathSpec == null || "".equals(pathSpec)) {
            pathSpec = "/";
        }
        if (endpoint.isMatchOnUriPrefix()) {
            pathSpec = pathSpec.endsWith("/") ? pathSpec + "*" : pathSpec + "/*";
        }
        context.addFilter(filterHolder, pathSpec, 0);
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

    protected SslSelectChannelConnector getSslSocketConnector(JettyHttpEndpoint endpoint) throws Exception {
        SslSelectChannelConnector answer = null;
        if (sslSocketConnectors != null) {
            answer = sslSocketConnectors.get(endpoint.getPort());
        }
        if (answer == null) {
            answer = createSslSocketConnector(endpoint);
        }
        return answer;
    }
    
    protected SslSelectChannelConnector createSslSocketConnector(JettyHttpEndpoint endpoint) throws Exception {
        SslSelectChannelConnector answer = null;
        
        // Note that this was set on the endpoint when it was constructed.  It was
        // either explicitly set at the component or on the endpoint, but either way,
        // the value is already set.  We therefore do not need to look at the component
        // level SSLContextParameters again in this method.
        SSLContextParameters endpointSslContextParameters = endpoint.getSslContextParameters();
        
        if (endpointSslContextParameters != null) {
            SslContextFactory contextFact = new SslContextFactory() {
                /**
                 * We are going to provide the context so none of the configuration options
                 * matter in the factory.  This method does not account for this scenario so
                 * we short-circuit it here to just let things go when the context is already
                 * provided.
                 */
                @Override
                public boolean checkConfig() {
                    if (getSslContext() == null) {
                        return super.checkConfig();
                    } else {
                        return true;
                    }
                }
                
            };
            contextFact.setSslContext(endpointSslContextParameters.createSSLContext());
            answer = new SslSelectChannelConnector(contextFact);
        } else {
            answer = new SslSelectChannelConnector();
            // with default null values, jetty ssl system properties
            // and console will be read by jetty implementation
    
            String keystoreProperty = System.getProperty(JETTY_SSL_KEYSTORE);
            if (keystoreProperty != null) {
                answer.getSslContextFactory().setKeyStore(keystoreProperty);
            } else if (sslKeystore != null) {
                answer.getSslContextFactory().setKeyStore(sslKeystore);
            }
    
            String keystorePassword = System.getProperty(JETTY_SSL_KEYPASSWORD);
            if (keystorePassword != null) {
                answer.getSslContextFactory().setKeyManagerPassword(keystorePassword);
            } else if (sslKeyPassword != null) {
                answer.getSslContextFactory().setKeyManagerPassword(sslKeyPassword);
            }
    
            String password = System.getProperty(JETTY_SSL_PASSWORD);
            if (password != null) {
                answer.getSslContextFactory().setKeyStorePassword(password);
            } else if (sslPassword != null) {
                answer.getSslContextFactory().setKeyStorePassword(sslPassword);
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
        return answer;
    }

    public Map<Integer, SslSelectChannelConnector> getSslSocketConnectors() {
        return sslSocketConnectors;
    }

    public void setSslSocketConnectors(Map <Integer, SslSelectChannelConnector> connectors) {
        sslSocketConnectors = connectors;
    }

    public SelectChannelConnector getSocketConnector(int port) throws Exception {
        SelectChannelConnector answer = null;
        if (socketConnectors != null) {
            answer = socketConnectors.get(port);
        }
        if (answer == null) {
            answer = createSocketConnector();
        }
        return answer;
    }

    protected SelectChannelConnector createSocketConnector() throws Exception {
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
        return answer;
    }

    public void setSocketConnectors(Map<Integer, SelectChannelConnector> socketConnectors) {
        this.socketConnectors = socketConnectors;
    }

    public synchronized HttpClient getHttpClient() throws Exception {
        if (httpClient == null) {
            httpClient = this.getNewHttpClient();
        }
        return httpClient;
    }
    
    public CamelHttpClient getNewHttpClient() throws Exception {
        CamelHttpClient httpClient = new CamelHttpClient();
        httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);

        if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
            String host = System.getProperty("http.proxyHost");
            int port = Integer.parseInt(System.getProperty("http.proxyPort"));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Java System Property http.proxyHost and http.proxyPort detected. Using http proxy host: {} port: {}", host, port);
            }
            httpClient.setProxy(new Address(host, port));
        }

        // use QueueThreadPool as the default bounded is deprecated (see SMXCOMP-157)
        if (getHttpClientThreadPool() == null) {
            QueuedThreadPool qtp = new QueuedThreadPool();
            if (httpClientMinThreads != null) {
                qtp.setMinThreads(httpClientMinThreads.intValue());
            }
            if (httpClientMaxThreads != null) {
                qtp.setMaxThreads(httpClientMaxThreads.intValue());
            }
            try {
                qtp.start();
            } catch (Exception e) {
                throw new RuntimeCamelException("Error starting JettyHttpClient thread pool: " + qtp, e);
            }
            setHttpClientThreadPool(qtp);
        }
        httpClient.setThreadPool(getHttpClientThreadPool());
        
        if (this.sslContextParameters != null) {
            ((CamelHttpClient) httpClient).setSSLContext(this.sslContextParameters.createSSLContext());
        }
        
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        if (httpClient != null) {
            this.isExplicitHttpClient = true;
        } else {
            this.isExplicitHttpClient = false;
        }
        this.httpClient = httpClient;
    }

    public ThreadPool getHttpClientThreadPool() {
        return httpClientThreadPool;
    }

    public void setHttpClientThreadPool(ThreadPool httpClientThreadPool) {
        this.httpClientThreadPool = httpClientThreadPool;
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

    // Implementation methods
    // -------------------------------------------------------------------------
    protected CamelServlet createServletForConnector(Server server, Connector connector,
                                                     List<Handler> handlers, JettyHttpEndpoint endpoint) throws Exception {
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        context.setConnectorNames(new String[] {connector.getName()});

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

        return camelServlet;
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
        if (httpClientThreadPool != null && httpClientThreadPool instanceof LifeCycle) {
            LifeCycle lc = (LifeCycle) httpClientThreadPool;
            lc.start();
        }
        if (httpClient != null && !httpClient.isStarted()) {
            httpClient.start();
        }
        
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
        if (httpClient != null) {
            httpClient.stop();
        }
        if (httpClientThreadPool != null && httpClientThreadPool instanceof LifeCycle) {
            LifeCycle lc = (LifeCycle) httpClientThreadPool;
            lc.stop();
        }
        if (mbContainer != null) {
            mbContainer.stop();
        }
    }
}
