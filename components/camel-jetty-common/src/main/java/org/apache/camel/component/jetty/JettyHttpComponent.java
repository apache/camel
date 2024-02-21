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
package org.apache.camel.component.jetty;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.RequestDispatcher;

import javax.management.MBeanServer;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpRestServletResolveConsumerStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.RestComponentHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.ee10.servlets.CrossOriginFilter;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An HttpComponent which starts an embedded Jetty for to handle consuming from the http endpoints.
 */
public abstract class JettyHttpComponent extends HttpCommonComponent
        implements RestConsumerFactory, RestApiConsumerFactory, SSLContextParametersAware {
    public static final String TMP_DIR = "CamelJettyTempDir";

    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(JettyHttpComponent.class);
    private static final String JETTY_SSL_KEYSTORE = "org.eclipse.jetty.ssl.keystore";
    private static final String JETTY_SSL_KEYPASSWORD = "org.eclipse.jetty.ssl.keypassword";
    private static final String JETTY_SSL_PASSWORD = "org.eclipse.jetty.ssl.password";

    protected String sslKeyPassword;
    protected String sslPassword;
    protected String sslKeystore;
    protected Map<Integer, Connector> sslSocketConnectors;
    protected Map<Integer, Connector> socketConnectors;
    protected Map<String, Object> sslSocketConnectorProperties;
    protected Map<String, Object> socketConnectorProperties;
    protected Integer minThreads;
    protected Integer maxThreads;
    protected ThreadPool threadPool;
    protected MBeanContainer mbContainer;
    protected boolean enableJmx;
    protected Long continuationTimeout;
    protected boolean useContinuation = true;
    protected SSLContextParameters sslContextParameters;
    protected boolean useGlobalSslContextParameters;
    protected Integer requestBufferSize;
    protected Integer requestHeaderSize;
    protected Integer responseBufferSize;
    protected Integer responseHeaderSize;
    protected String proxyHost;
    protected ErrorHandler errorHandler;
    protected boolean useXForwardedForHeader;
    private Integer proxyPort;
    private boolean sendServerVersion = true;
    private QueuedThreadPool defaultQueuedThreadPool;
    private String filesLocation;
    private Long maxFileSize = -1L;
    private Long maxRequestSize = -1L;
    private Integer fileSizeThreshold = 0;

    protected JettyHttpComponent() {
    }

    static class ConnectorRef {
        final Server server;
        final Connector connector;
        final CamelServlet servlet;
        int refCount;

        ConnectorRef(Server server, Connector connector, CamelServlet servlet) {
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
        Boolean enableJmx = getAndRemoveParameter(parameters, "enableJmx", Boolean.class);
        Boolean enableMultipartFilter = getAndRemoveParameter(parameters, "enableMultipartFilter",
                Boolean.class, true);
        Filter multipartFilter = resolveAndRemoveReferenceParameter(parameters, "multipartFilterRef", Filter.class);
        List<Filter> filters = resolveAndRemoveReferenceListParameter(parameters, "filters", Filter.class);
        Boolean enableCors = getAndRemoveParameter(parameters, "enableCORS", Boolean.class, false);
        HeaderFilterStrategy headerFilterStrategy
                = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        SSLContextParameters sslContextParameters
                = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        SSLContextParameters ssl = sslContextParameters != null ? sslContextParameters : this.sslContextParameters;
        ssl = ssl != null ? ssl : retrieveGlobalSslContextParameters();
        String proxyHost = getAndRemoveParameter(parameters, "proxyHost", String.class, getProxyHost());
        Integer proxyPort = getAndRemoveParameter(parameters, "proxyPort", Integer.class, getProxyPort());
        Boolean async = getAndRemoveParameter(parameters, "async", Boolean.class);
        boolean muteException = getAndRemoveParameter(parameters, "muteException", boolean.class, isMuteException());
        String filesLocation = getAndRemoveParameter(parameters, "filesLocation", String.class, getFilesLocation());
        Integer fileSizeThreshold
                = getAndRemoveParameter(parameters, "fileSizeThreshold", Integer.class, getFileSizeThreshold());
        Long maxFileSize = getAndRemoveParameter(parameters, "maxFileSize", Long.class, getMaxFileSize());
        Long maxRequestSize = getAndRemoveParameter(parameters, "maxRequestSize", Long.class, getMaxRequestSize());

        // extract filterInit. parameters
        Map filterInitParameters = PropertiesHelper.extractProperties(parameters, "filterInit.");

        URI addressUri = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        URI endpointUri = URISupport.createRemainingURI(addressUri, parameters);
        // need to keep the httpMethodRestrict parameter for the endpointUri
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(addressUri, parameters);
        // create endpoint after all known parameters have been extracted from parameters

        // include component scheme in the uri
        String scheme = StringHelper.before(uri, ":");
        endpointUri = new URI(scheme + ":" + endpointUri);

        JettyHttpEndpoint endpoint = createEndpoint(endpointUri, httpUri);
        if (async != null) {
            endpoint.setAsync(async);
        }
        endpoint.setMuteException(muteException);

        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            setEndpointHeaderFilterStrategy(endpoint);
        }
        // setup the proxy host and proxy port
        if (proxyHost != null) {
            endpoint.setProxyHost(proxyHost);
            endpoint.setProxyPort(proxyPort);
        }
        if (!filterInitParameters.isEmpty()) {
            endpoint.setFilterInitParameters(filterInitParameters);
        }
        if (!handlerList.isEmpty()) {
            endpoint.setHandlers(handlerList);
        }
        // prefer to use endpoint configured over component configured
        if (binding == null) {
            // fallback to component configured
            binding = getHttpBinding();
        }
        if (binding != null) {
            endpoint.setHttpBinding(binding);
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
        if (enableCors) {
            endpoint.setEnableCORS(enableCors);
            if (filters == null) {
                filters = new ArrayList<>(1);
            }
            filters.add(new CrossOriginFilter());
        }
        if (filters != null) {
            endpoint.setFilters(filters);
        }

        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }
        if (ssl != null) {
            endpoint.setSslContextParameters(ssl);
        }
        endpoint.setSendServerVersion(isSendServerVersion());
        endpoint.setFilesLocation(filesLocation);
        endpoint.setFileSizeThreshold(fileSizeThreshold);
        endpoint.setMaxFileSize(maxFileSize);
        endpoint.setMaxRequestSize(maxRequestSize);
        setProperties(endpoint, parameters);

        // re-create http uri after all parameters has been set on the endpoint, as the remainders are for http uri
        httpUri = URISupport.createRemainingURI(addressUri, parameters);
        endpoint.setHttpUri(httpUri);

        return endpoint;
    }

    protected abstract JettyHttpEndpoint createEndpoint(URI endpointUri, URI httpUri) throws URISyntaxException;

    @Override
    public boolean canConnect(HttpConsumer consumer) throws Exception {
        // Make sure that there is a connector for the requested endpoint.
        JettyHttpEndpoint endpoint = (JettyHttpEndpoint) consumer.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);

            // check if there are already another consumer on the same context-path and if so fail
            if (connectorRef != null) {
                for (Map.Entry<String, HttpConsumer> entry : connectorRef.servlet.getConsumers().entrySet()) {
                    String path = entry.getValue().getPath();
                    CamelContext camelContext = entry.getValue().getEndpoint().getCamelContext();
                    if (consumer.getPath().equals(path)) {
                        // its allowed if they are from the same camel context
                        boolean sameContext = consumer.getEndpoint().getCamelContext() == camelContext;
                        if (!sameContext) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        // Make sure that there is a connector for the requested endpoint.
        JettyHttpEndpoint endpoint = (JettyHttpEndpoint) consumer.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Server server = createServer();
                Connector connector = getConnector(server, endpoint);
                if ("localhost".equalsIgnoreCase(endpoint.getHttpUri().getHost())) {
                    LOG.warn("You use localhost interface! It means that no external connections will be available. "
                             + "Don't you want to use 0.0.0.0 instead (all network interfaces)? {}",
                            endpoint);
                }
                if (endpoint.isEnableJmx()) {
                    enableJmx(server);
                }
                server.addConnector(connector);

                connectorRef = new ConnectorRef(
                        server, connector,
                        createServletForConnector(server, connector, endpoint.getHandlers(), endpoint));
                // must enable session before we start
                if (endpoint.isSessionSupport()) {
                    enableSessionSupport(connectorRef.server, connectorKey);
                }
                connectorRef.server.start();

                LOG.debug("Adding connector key: {} -> {}", connectorKey, connectorRef);
                CONNECTORS.put(connectorKey, connectorRef);

            } else {
                LOG.debug("Using existing connector key: {} -> {}", connectorKey, connectorRef);

                // check if there are any new handlers, and if so then we need to re-start the server
                if (endpoint.getHandlers() != null && !endpoint.getHandlers().isEmpty()) {
                    List<Handler> existingHandlers = new ArrayList<>();
                    if (connectorRef.server.getHandlers() != null && !connectorRef.server.getHandlers().isEmpty()) {
                        existingHandlers = connectorRef.server.getHandlers();
                    }
                    List<Handler> newHandlers = new ArrayList<>(endpoint.getHandlers());
                    boolean changed = !existingHandlers.containsAll(newHandlers) && !newHandlers.containsAll(existingHandlers);
                    if (changed) {
                        LOG.debug("Restarting Jetty server due to adding new Jetty Handlers: {}", newHandlers);
                        connectorRef.server.stop();
                        addJettyHandlers(connectorRef.server, endpoint.getHandlers());
                        connectorRef.server.start();
                    }
                }
                // check the session support
                if (endpoint.isSessionSupport()) {
                    enableSessionSupport(connectorRef.server, connectorKey);
                }
                // ref track the connector
                connectorRef.increment();
            }

            if (endpoint.isEnableMultipartFilter()) {
                enableMultipartFilter(endpoint, connectorRef.server);
            }

            if (endpoint.getFilters() != null && !endpoint.getFilters().isEmpty()) {
                setFilters(endpoint, connectorRef.server);
            }
            connectorRef.servlet.connect(consumer);
        }
    }

    private void enableJmx(Server server) {
        MBeanContainer containerToRegister = getMbContainer();
        if (containerToRegister != null) {
            LOG.info("Jetty JMX Extensions is enabled");
            addServerMBean(server);
            // Since we may have many Servers running, don't tie the MBeanContainer
            // to a Server lifecycle or we end up closing it while it is still in use.
            //server.addBean(mbContainer);
        }
    }

    private void enableSessionSupport(Server server, String connectorKey) throws Exception {
        ServletContextHandler context = server.getDescendant(ServletContextHandler.class);
        if (context.getSessionHandler() == null) {
            SessionHandler sessionHandler = new SessionHandler();
            if (context.isStarted()) {
                throw new IllegalStateException(
                        "Server has already been started. Cannot enabled sessionSupport on " + connectorKey);
            } else {
                context.setSessionHandler(sessionHandler);
            }
        }
    }

    private void setFilters(JettyHttpEndpoint endpoint, Server server) {
        ServletContextHandler context = server.getDescendant(ServletContextHandler.class);
        List<Filter> filters = endpoint.getFilters();
        for (Filter filter : filters) {
            FilterHolder filterHolder = new FilterHolder();
            if (endpoint.getFilterInitParameters() != null) {
                filterHolder.setInitParameters(endpoint.getFilterInitParameters());
            }
            addFilter(endpoint, filter, filterHolder, context);
        }
    }

    private void addFilter(
            JettyHttpEndpoint endpoint, Filter filter, FilterHolder filterHolder, ServletContextHandler context) {
        addFilter(endpoint, filterHolder, filter, context);
    }

    private void addFilter(ServletContextHandler context, FilterHolder filterHolder, String pathSpec) {
        context.getServletHandler().addFilterWithMapping(filterHolder, pathSpec, 0);
    }

    private void enableMultipartFilter(HttpCommonEndpoint endpoint, Server server) throws Exception {
        ServletContextHandler context = server.getDescendant(ServletContextHandler.class);
        CamelContext camelContext = this.getCamelContext();
        FilterHolder filterHolder = new FilterHolder();
        filterHolder.setInitParameter("deleteFiles", "true");
        if (ObjectHelper.isNotEmpty(camelContext.getGlobalOption(TMP_DIR))) {
            File file = new File(camelContext.getGlobalOption(TMP_DIR));
            if (!file.isDirectory()) {
                throw new RuntimeCamelException(
                        "The temp file directory of camel-jetty is not exists, please recheck it with directory name :"
                                                + camelContext.getGlobalOptions().get(TMP_DIR));
            }
            context.setAttribute("jakarta.servlet.context.tempdir", file);
        }
        // if a filter ref was provided, use it.
        Filter filter = ((JettyHttpEndpoint) endpoint).getMultipartFilter();
        if (filter == null) {
            // if no filter ref was provided, use the default filter
            filter = new MultiPartFilter();
        }
        final String pathSpec = addFilter(endpoint, filterHolder, filter, context);
        LOG.debug("using multipart filter implementation {} for path {}", filter.getClass().getName(), pathSpec);
    }

    private String addFilter(
            HttpCommonEndpoint endpoint, FilterHolder filterHolder, Filter filter, ServletContextHandler context) {
        filterHolder.setFilter(new CamelFilterWrapper(filter));
        String pathSpec = endpoint.getPath();
        if (pathSpec == null || pathSpec.isEmpty()) {
            pathSpec = "/";
        }
        if (endpoint.isMatchOnUriPrefix()) {
            pathSpec = pathSpec.endsWith("/") ? pathSpec + "*" : pathSpec + "/*";
        }
        addFilter(context, filterHolder, pathSpec);
        return pathSpec;
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     */
    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        // If the connector is not needed anymore then stop it
        HttpCommonEndpoint endpoint = consumer.getEndpoint();
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
                        this.removeServerMBean(connectorRef.server);
                        //mbContainer.removeBean(connectorRef.connector);
                    }
                    if (defaultQueuedThreadPool != null) {
                        try {
                            defaultQueuedThreadPool.stop();
                        } catch (Exception t) {
                            defaultQueuedThreadPool.destroy();
                        } finally {
                            defaultQueuedThreadPool = null;
                        }
                    }
                }
            }
        }
    }

    private String getConnectorKey(HttpCommonEndpoint endpoint) {
        return endpoint.getProtocol() + ":" + endpoint.getHttpUri().getHost() + ":" + endpoint.getPort();
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    /**
     * The key password, which is used to access the certificate's key entry in the keystore (this is the same password
     * that is supplied to the keystore command's -keypass option).
     */
    @Metadata(description = "The key password, which is used to access the certificate's key entry in the keystore "
                            + "(this is the same password that is supplied to the keystore command's -keypass option).",
              label = "security", secret = true)
    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslPassword() {
        return sslPassword;
    }

    /**
     * The ssl password, which is required to access the keystore file (this is the same password that is supplied to
     * the keystore command's -storepass option).
     */
    @Metadata(description = "The ssl password, which is required to access the keystore file (this is the same password that is supplied to the keystore command's -storepass option).",
              label = "security", secret = true)
    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }

    /**
     * Specifies the location of the Java keystore file, which contains the Jetty server's own X.509 certificate in a
     * key entry.
     */
    @Metadata(description = "Specifies the location of the Java keystore file, which contains the Jetty server's own X.509 certificate in a key entry.",
              label = "security", secret = true)
    public void setKeystore(String sslKeystore) {
        this.sslKeystore = sslKeystore;
    }

    public String getKeystore() {
        return sslKeystore;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * This option is used to set the ErrorHandler that Jetty server uses.
     */
    @Metadata(description = "This option is used to set the ErrorHandler that Jetty server uses.", label = "advanced")
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    protected Connector getConnector(Server server, JettyHttpEndpoint endpoint) {
        Connector connector;
        if ("https".equals(endpoint.getProtocol())) {
            connector = getSslSocketConnector(server, endpoint);
        } else {
            connector = getSocketConnector(server, endpoint);
        }
        return connector;
    }

    protected Connector getSocketConnector(Server server, JettyHttpEndpoint endpoint) {
        Connector answer = null;
        if (socketConnectors != null) {
            answer = socketConnectors.get(endpoint.getPort());
        }
        if (answer == null) {
            answer = createConnector(server, endpoint);
        }
        return answer;
    }

    protected Connector getSslSocketConnector(Server server, JettyHttpEndpoint endpoint) {
        Connector answer = null;
        if (sslSocketConnectors != null) {
            answer = sslSocketConnectors.get(endpoint.getPort());
        }
        if (answer == null) {
            answer = createConnector(server, endpoint);
        }
        return answer;
    }

    protected Connector createConnector(Server server, JettyHttpEndpoint endpoint) {

        // now we just use the SelectChannelConnector as the default connector
        SslContextFactory.Server sslcf = null;

        // Note that this was set on the endpoint when it was constructed.  It was
        // either explicitly set at the component or on the endpoint, but either way,
        // the value is already set.  We therefore do not need to look at the component
        // level SSLContextParameters again in this method.
        SSLContextParameters endpointSslContextParameters = endpoint.getSslContextParameters();

        if (endpointSslContextParameters != null) {
            try {
                sslcf = (SslContextFactory.Server) createSslContextFactory(endpointSslContextParameters, false);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else if ("https".equals(endpoint.getProtocol())) {
            sslcf = new SslContextFactory.Server();
            sslcf.setEndpointIdentificationAlgorithm(null);
            String keystoreProperty = System.getProperty(JETTY_SSL_KEYSTORE);
            if (keystoreProperty != null) {
                sslcf.setKeyStorePath(keystoreProperty);
            } else if (sslKeystore != null) {
                sslcf.setKeyStorePath(sslKeystore);
            }

            String keystorePassword = System.getProperty(JETTY_SSL_KEYPASSWORD);
            if (keystorePassword != null) {
                sslcf.setKeyManagerPassword(keystorePassword);
            } else if (sslKeyPassword != null) {
                sslcf.setKeyManagerPassword(sslKeyPassword);
            }

            String password = System.getProperty(JETTY_SSL_PASSWORD);
            if (password != null) {
                sslcf.setKeyStorePassword(password);
            } else if (sslPassword != null) {
                sslcf.setKeyStorePassword(sslPassword);
            }
        }

        return createConnectorJettyInternal(server, endpoint, sslcf);
    }

    protected abstract AbstractConnector createConnectorJettyInternal(
            Server server, JettyHttpEndpoint endpoint, SslContextFactory.Server sslcf);

    private SslContextFactory createSslContextFactory(SSLContextParameters ssl, boolean client)
            throws GeneralSecurityException, IOException {
        SslContextFactory answer;
        if (!client) {
            answer = new SslContextFactory.Server();
        } else {
            answer = new SslContextFactory.Client();
        }
        if (ssl != null) {
            answer.setSslContext(ssl.createSSLContext(getCamelContext()));
        }

        // jetty default is
        // addExcludeProtocols("SSL", "SSLv2", "SSLv2Hello", "SSLv3");
        // setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$");

        // configure include/exclude ciphers and protocols
        if (ssl != null && ssl.getCipherSuitesFilter() != null) {
            List<String> includeCiphers = ssl.getCipherSuitesFilter().getInclude();
            if (includeCiphers != null && !includeCiphers.isEmpty()) {
                String[] arr = includeCiphers.toArray(new String[0]);
                answer.setIncludeCipherSuites(arr);
            } else {
                answer.setIncludeCipherSuites(".*");
            }
            List<String> excludeCiphers = ssl.getCipherSuitesFilter().getExclude();
            if (excludeCiphers != null && !excludeCiphers.isEmpty()) {
                String[] arr = excludeCiphers.toArray(new String[0]);
                answer.setExcludeCipherSuites(arr);
            }
        }
        if (ssl != null && ssl.getSecureSocketProtocolsFilter() != null) {
            List<String> includeProtocols = ssl.getSecureSocketProtocolsFilter().getInclude();
            if (includeProtocols != null && !includeProtocols.isEmpty()) {
                String[] arr = includeProtocols.toArray(new String[0]);
                answer.setIncludeProtocols(arr);
            } else {
                answer.setIncludeProtocols(".*");
            }
            List<String> excludeProtocols = ssl.getSecureSocketProtocolsFilter().getExclude();
            if (excludeProtocols != null && !excludeProtocols.isEmpty()) {
                String[] arr = excludeProtocols.toArray(new String[0]);
                answer.setExcludeProtocols(arr);
            }
        }

        return answer;
    }

    protected boolean checkSSLContextFactoryConfig(Object instance) {
        try {
            Method method = instance.getClass().getMethod("checkConfig");
            return (Boolean) method.invoke(instance);
        } catch (IllegalArgumentException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            // ignore
        }
        return false;
    }

    public Map<Integer, Connector> getSslSocketConnectors() {
        return sslSocketConnectors;
    }

    /**
     * A map which contains per port number specific SSL connectors.
     */
    @Metadata(description = "A map which contains per port number specific SSL connectors.", label = "security")
    public void setSslSocketConnectors(Map<Integer, Connector> connectors) {
        sslSocketConnectors = connectors;
    }

    /**
     * A map which contains per port number specific HTTP connectors. Uses the same principle as sslSocketConnectors.
     */
    @Metadata(description = "A map which contains per port number specific HTTP connectors. Uses the same principle as sslSocketConnectors.",
              label = "security")
    public void setSocketConnectors(Map<Integer, Connector> socketConnectors) {
        this.socketConnectors = socketConnectors;
    }

    public Map<Integer, Connector> getSocketConnectors() {
        return socketConnectors;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    /**
     * To set a value for minimum number of threads in server thread pool. Notice that both a min and max size must be
     * configured.
     */
    @Metadata(description = "To set a value for minimum number of threads in server thread pool. Notice that both a min and max size must be configured.",
              label = "consumer")
    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    /**
     * To set a value for maximum number of threads in server thread pool. Notice that both a min and max size must be
     * configured.
     */
    @Metadata(description = "To set a value for maximum number of threads in server thread pool. Notice that both a min and max size must be configured.",
              label = "consumer")
    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    /**
     * To use a custom thread pool for the server. This option should only be used in special circumstances.
     */
    @Metadata(description = "To use a custom thread pool for the server. This option should only be used in special circumstances.",
              label = "consumer,advanced")
    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public boolean isEnableJmx() {
        return enableJmx;
    }

    /**
     * If this option is true, Jetty JMX support will be enabled for this endpoint.
     */
    @Metadata(description = "If this option is true, Jetty JMX support will be enabled for this endpoint.")
    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    /**
     * Not to be used - use JettyHttpBinding instead.
     */
    @Override
    @Metadata(description = "Not to be used - use JettyHttpBinding instead.", label = "advanced")
    public void setHttpBinding(HttpBinding httpBinding) {
        throw new IllegalArgumentException("Not to be used - use JettyHttpBinding instead.");
    }

    /**
     * Jetty component does not use HttpConfiguration.
     */
    @Override
    @Metadata(description = "Jetty component does not use HttpConfiguration.", label = "advanced")
    public void setHttpConfiguration(HttpConfiguration httpConfiguration) {
        throw new IllegalArgumentException("Jetty component does not use HttpConfiguration.");
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
            } else {
                LOG.warn("JMX disabled in CamelContext. Jetty JMX extensions will remain disabled.");
            }
        }

        return this.mbContainer;
    }

    /**
     * To use a existing configured org.eclipse.jetty.jmx.MBeanContainer if JMX is enabled that Jetty uses for
     * registering mbeans.
     */
    @Metadata(description = "To use a existing configured org.eclipse.jetty.jmx.MBeanContainer if JMX is enabled that Jetty uses for registering mbeans.",
              label = "advanced")
    public void setMbContainer(MBeanContainer mbContainer) {
        this.mbContainer = mbContainer;
    }

    public Map<String, Object> getSslSocketConnectorProperties() {
        return sslSocketConnectorProperties;
    }

    /**
     * A map which contains general SSL connector properties.
     */
    @Metadata(description = "A map which contains general SSL connector properties.", label = "security")
    public void setSslSocketConnectorProperties(Map<String, Object> sslSocketConnectorProperties) {
        this.sslSocketConnectorProperties = sslSocketConnectorProperties;
    }

    public Map<String, Object> getSocketConnectorProperties() {
        return socketConnectorProperties;
    }

    /**
     * A map which contains general HTTP connector properties. Uses the same principle as sslSocketConnectorProperties.
     */
    @Metadata(description = "A map which contains general HTTP connector properties. Uses the same principle as sslSocketConnectorProperties.",
              label = "security")
    public void setSocketConnectorProperties(Map<String, Object> socketConnectorProperties) {
        this.socketConnectorProperties = socketConnectorProperties;
    }

    public void addSocketConnectorProperty(String key, Object value) {
        if (socketConnectorProperties == null) {
            socketConnectorProperties = new HashMap<>();
        }
        socketConnectorProperties.put(key, value);
    }

    public void addSslSocketConnectorProperty(String key, Object value) {
        if (sslSocketConnectorProperties == null) {
            sslSocketConnectorProperties = new HashMap<>();
        }
        sslSocketConnectorProperties.put(key, value);
    }

    public Long getContinuationTimeout() {
        return continuationTimeout;
    }

    /**
     * Allows to set a timeout in millis when using Jetty as consumer (server). By default Jetty uses 30000. You can use
     * a value of <= 0 to never expire. If a timeout occurs then the request will be expired and Jetty will return back
     * a http error 503 to the client. This option is only in use when using Jetty with the Asynchronous Routing Engine.
     */
    @Metadata(description = "Allows to set a timeout in millis when using Jetty as consumer (server)."
                            + " By default Jetty uses 30000. You can use a value of <= 0 to never expire."
                            + " If a timeout occurs then the request will be expired and Jetty will return back a http error 503 to the client."
                            + " This option is only in use when using Jetty with the Asynchronous Routing Engine.",
              defaultValue = "30000", label = "consumer")
    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public boolean isUseContinuation() {
        return useContinuation;
    }

    /**
     * Whether or not to use Jetty continuations for the Jetty Server.
     */
    @Metadata(description = "Whether or not to use Jetty continuations for the Jetty Server.", defaultValue = "true",
              label = "consumer")
    public void setUseContinuation(boolean useContinuation) {
        this.useContinuation = useContinuation;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    @Metadata(description = "To configure security using SSLContextParameters", label = "security")
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters
     */
    @Override
    @Metadata(description = "Enable usage of global SSL context parameters", label = "security", defaultValue = "false")
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public Integer getResponseBufferSize() {
        return responseBufferSize;
    }

    /**
     * Allows to configure a custom value of the response buffer size on the Jetty connectors.
     */
    @Metadata(description = "Allows to configure a custom value of the response buffer size on the Jetty connectors.")
    public void setResponseBufferSize(Integer responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

    public Integer getRequestBufferSize() {
        return requestBufferSize;
    }

    /**
     * Allows to configure a custom value of the request buffer size on the Jetty connectors.
     */
    @Metadata(description = "Allows to configure a custom value of the request buffer size on the Jetty connectors.")
    public void setRequestBufferSize(Integer requestBufferSize) {
        this.requestBufferSize = requestBufferSize;
    }

    public Integer getRequestHeaderSize() {
        return requestHeaderSize;
    }

    /**
     * Allows to configure a custom value of the request header size on the Jetty connectors.
     */
    @Metadata(description = "Allows to configure a custom value of the request header size on the Jetty connectors.")
    public void setRequestHeaderSize(Integer requestHeaderSize) {
        this.requestHeaderSize = requestHeaderSize;
    }

    public Integer getResponseHeaderSize() {
        return responseHeaderSize;
    }

    /**
     * Allows to configure a custom value of the response header size on the Jetty connectors.
     */
    @Metadata(description = "Allows to configure a custom value of the response header size on the Jetty connectors.")
    public void setResponseHeaderSize(Integer responseHeaderSize) {
        this.responseHeaderSize = responseHeaderSize;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To use a http proxy to configure the hostname.
     */
    @Metadata(description = "To use a http proxy to configure the hostname.", label = "proxy")
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To use a http proxy to configure the port number.
     */
    @Metadata(description = "To use a http proxy to configure the port number.", label = "proxy")
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isUseXForwardedForHeader() {
        return useXForwardedForHeader;
    }

    /**
     * To use the X-Forwarded-For header in HttpServletRequest.getRemoteAddr.
     */
    @Metadata(description = "To use the X-Forwarded-For header in HttpServletRequest.getRemoteAddr.")
    public void setUseXForwardedForHeader(boolean useXForwardedForHeader) {
        this.useXForwardedForHeader = useXForwardedForHeader;
    }

    public boolean isSendServerVersion() {
        return sendServerVersion;
    }

    /**
     * If the option is true, jetty will send the server header with the jetty version information to the client which
     * sends the request. NOTE please make sure there is no any other camel-jetty endpoint is share the same port,
     * otherwise this option may not work as expected.
     */
    @Metadata(description = "If the option is true, jetty will send the server header with the jetty version information to the client which sends the request."
                            + " NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.",
              defaultValue = "true", label = "consumer")
    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    @Metadata(description = "The maximum size allowed for uploaded files. -1 means no limit",
              defaultValue = "-1", label = "consumer,advanced")
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public long getMaxRequestSize() {
        return maxRequestSize;
    }

    @Metadata(description = "The maximum size allowed for multipart/form-data requests. -1 means no limit",
              defaultValue = "-1", label = "consumer,advanced")
    public void setMaxRequestSize(long maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public int getFileSizeThreshold() {
        return fileSizeThreshold;
    }

    @Metadata(description = "The size threshold after which files will be written to disk for multipart/form-data requests. By default the files are not written to disk",
              defaultValue = "0", label = "consumer,advanced")
    public void setFileSizeThreshold(int fileSizeThreshold) {
        this.fileSizeThreshold = fileSizeThreshold;
    }

    public String getFilesLocation() {
        return filesLocation;
    }

    @Metadata(description = "The directory location where files will be store for multipart/form-data requests. By default the files are written in the system temporary folder",
              label = "consumer,advanced")
    public void setFilesLocation(String filesLocation) {
        this.filesLocation = filesLocation;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    public Consumer createConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, configuration,
                parameters, false);
    }

    @Override
    public Consumer createApiConsumer(
            CamelContext camelContext, Processor processor, String contextPath,
            RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
            RestConfiguration configuration, Map<String, Object> parameters, boolean api)
            throws Exception {

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
            config = CamelContextHelper.getRestConfiguration(getCamelContext(), "jetty");
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

        Map<String, Object> map = RestComponentHelper.initRestEndpointProperties("jetty", config);

        boolean cors = config.isEnableCORS();
        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
        }

        if (api) {
            map.put("matchOnUriPrefix", "true");
        }

        RestComponentHelper.addHttpRestrictParam(map, verb, cors);

        String url = RestComponentHelper.createRestConsumerUrl("jetty", scheme, host, port, path, map);

        JettyHttpEndpoint endpoint = (JettyHttpEndpoint) camelContext.getEndpoint(url, parameters);

        if (!map.containsKey("httpBindingRef")) {
            // use the rest binding, if not using a custom http binding
            endpoint.setHttpBinding(new JettyRestHttpBinding(endpoint));
            // disable this filter as we want to use ours
            endpoint.setEnableMultipartFilter(false);
        }

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return consumer;
    }

    protected CamelServlet createServletForConnector(
            Server server, Connector connector,
            List<Handler> handlers, JettyHttpEndpoint endpoint)
            throws Exception {
        ServletContextHandler context
                = new ServletContextHandler("/", false, false);
        server.setHandler(context);

        addJettyHandlers(server, handlers);

        CamelServlet camelServlet = new CamelContinuationServlet();
        ServletHolder holder = new ServletHolder();
        holder.setServlet(camelServlet);
        holder.setAsyncSupported(true);
        holder.setInitParameter(CamelServlet.ASYNC_PARAM, Boolean.toString(endpoint.isAsync()));
        context.addServlet(holder, "/*");

        String location = endpoint.getFilesLocation();
        if (location == null) {
            File file = File.createTempFile("camel", "");
            if (!FileUtil.deleteFile(file)) {
                LOG.error("failed to delete {}", file);
            }
            location = file.getParentFile().getAbsolutePath();
        }

        //must register the MultipartConfig to make jetty server multipart aware
        holder.getRegistration()
                .setMultipartConfig(new MultipartConfigElement(
                        location, endpoint.getMaxFileSize(), endpoint.getMaxRequestSize(), endpoint.getFileSizeThreshold()));

        // use rest enabled resolver in case we use rest
        camelServlet.setServletResolveConsumerStrategy(new HttpRestServletResolveConsumerStrategy());

        return camelServlet;
    }

    protected void addJettyHandlers(Server server, List<Handler> handlers) {
        if (handlers != null && !handlers.isEmpty()) {
            for (Handler handler : handlers) {
                if (handler instanceof Handler.Wrapper) {
                    // avoid setting a handler more than once
                    if (!isHandlerInChain(server.getHandler(), handler)) {
                        ((Handler.Wrapper) handler).setHandler(server.getHandler());
                        server.setHandler(handler);
                    }
                } else {
                    ContextHandlerCollection handlerCollection = new ContextHandlerCollection();
                    handlerCollection.addHandler(server.getHandler());
                    handlerCollection.addHandler(handler);
                    server.setHandler(handlerCollection);
                }
            }
        }
    }

    protected boolean isHandlerInChain(Handler current, Handler handler) {

        if (handler.equals(current)) {
            //Found a match in the chain
            return true;
        } else if (current instanceof Handler.Wrapper) {
            //Inspect the next handler in the chain
            return isHandlerInChain(((Handler.Wrapper) current).getHandler(), handler);
        } else {
            //End of chain
            return false;
        }
    }

    protected Server createServer() {

        ThreadPool tp = threadPool;
        defaultQueuedThreadPool = null;
        // configure thread pool if min/max given
        if (minThreads != null || maxThreads != null) {
            if (getThreadPool() != null) {
                throw new IllegalArgumentException(
                        "You cannot configure both minThreads/maxThreads and a custom threadPool on JettyHttpComponent: "
                                                   + this);
            }
            defaultQueuedThreadPool = new QueuedThreadPool();
            if (minThreads != null) {
                defaultQueuedThreadPool.setMinThreads(minThreads.intValue());
            }
            if (maxThreads != null) {
                defaultQueuedThreadPool.setMaxThreads(maxThreads.intValue());
            }
            tp = defaultQueuedThreadPool;

        }

        Server s;
        if (tp != null) {
            s = new Server(tp);
        } else {
            s = new Server();
        }
        if (isEnableJmx()) {
            enableJmx(s);
        }

        if (defaultQueuedThreadPool != null) {
            // let the thread names indicate they are from the server
            defaultQueuedThreadPool.setName("CamelJettyServer(" + ObjectHelper.getIdentityHashCode(s) + ")");
            try {
                defaultQueuedThreadPool.start();
            } catch (Exception e) {
                throw new RuntimeCamelException("Error starting JettyServer thread pool: " + defaultQueuedThreadPool, e);
            }
        }
        ContextHandlerCollection collection = new ContextHandlerCollection();
        s.setHandler(collection);
        // setup the error handler if it set to Jetty component
        if (getErrorHandler() != null) {
            s.setErrorHandler(getErrorHandler());
        } else {
            //need an error handler that won't leak information about the exception back to the client.
            ErrorHandler eh = new ErrorHandler() {
                @Override
                public boolean handle(
                        Request baseRequest, Response response, Callback callback)
                        throws Exception {
                    String msg = HttpStatus.getMessage(response.getStatus());
                    Object timeout = baseRequest.getAttribute(CamelContinuationServlet.TIMEOUT_ERROR);
                    if (Boolean.TRUE.equals(timeout)) {
                        baseRequest.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 504);
                        response.setStatus(504);
                    }
                    baseRequest.setAttribute(RequestDispatcher.ERROR_MESSAGE, msg);
                    return super.handle(baseRequest, response, callback);
                }
            };
            s.setErrorHandler(eh);
        }
        return s;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        try {
            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), "jetty");

            // configure additional options on jetty configuration
            if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
                setProperties(this, config.getComponentProperties());
            }
        } catch (IllegalArgumentException e) {
            // if there's a mismatch between the component and the rest-configuration,
            // then getRestConfiguration throws IllegalArgumentException which can be
            // safely ignored as it means there's no special conf for this component.
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (!CONNECTORS.isEmpty()) {
            for (Map.Entry<String, ConnectorRef> connectorEntry : CONNECTORS.entrySet()) {
                ConnectorRef connectorRef = connectorEntry.getValue();
                if (connectorRef != null && connectorRef.getRefCount() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    // Camel controls the lifecycle of these entities so remove the
                    // registered MBeans when Camel is done with the managed objects.
                    removeServerMBean(connectorRef.server);
                    connectorRef.server.stop();
                    //removeServerMBean(connectorRef.connector);
                    CONNECTORS.remove(connectorEntry.getKey());
                }
            }
        }
        if (mbContainer != null) {
            mbContainer.destroy();
            mbContainer = null;
        }
    }

    private void addServerMBean(Server server) {
        if (mbContainer == null) {
            return;
        }

        try {
            Object o = getContainer(server);
            o.getClass().getMethod("addEventListener", EventListener.class).invoke(o, mbContainer);
            mbContainer.getClass().getMethod("beanAdded", Container.class, Object.class)
                    .invoke(mbContainer, null, server);
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception r) {
            throw new RuntimeException(r);
        }
    }

    private void removeServerMBean(Server server) {
        try {
            mbContainer.getClass().getMethod("beanRemoved", Container.class, Object.class)
                    .invoke(mbContainer, null, server);
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception r) {
            try {
                mbContainer.getClass().getMethod("removeBean", Object.class)
                        .invoke(mbContainer, server);
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception r2) {
                throw new RuntimeException(r);
            }
        }
    }

    private static Container getContainer(Object server) {
        if (server instanceof Container) {
            return (Container) server;
        }
        try {
            return (Container) server.getClass().getMethod("getContainer").invoke(server);
        } catch (RuntimeException t) {
            throw t;
        } catch (Exception t) {
            throw new RuntimeException(t);
        }
    }

}
