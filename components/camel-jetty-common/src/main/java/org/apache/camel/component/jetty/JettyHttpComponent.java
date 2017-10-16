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
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpRestHeaderFilterStrategy;
import org.apache.camel.http.common.HttpRestServletResolveConsumerStrategy;
import org.apache.camel.http.common.UrlRewrite;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.servlets.MultiPartFilter;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.ssl.SslContextFactory;
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
public abstract class JettyHttpComponent extends HttpCommonComponent implements RestConsumerFactory, RestApiConsumerFactory, RestProducerFactory, SSLContextParametersAware {
    public static final String TMP_DIR = "CamelJettyTempDir";

    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();

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

    public JettyHttpComponent() {
        super(JettyHttpEndpoint.class);
    }

    class ConnectorRef {
        Server server;
        Connector connector;
        CamelServlet servlet;
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
        JettyHttpBinding jettyBinding = resolveAndRemoveReferenceParameter(parameters, "jettyHttpBindingRef", JettyHttpBinding.class);
        Boolean enableJmx = getAndRemoveParameter(parameters, "enableJmx", Boolean.class);
        Boolean enableMultipartFilter = getAndRemoveParameter(parameters, "enableMultipartFilter",
                                                              Boolean.class, true);
        Filter multipartFilter = resolveAndRemoveReferenceParameter(parameters, "multipartFilterRef", Filter.class);
        List<Filter> filters = resolveAndRemoveReferenceListParameter(parameters, "filtersRef", Filter.class);
        Boolean enableCors = getAndRemoveParameter(parameters, "enableCORS", Boolean.class, false);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        UrlRewrite urlRewrite = resolveAndRemoveReferenceParameter(parameters, "urlRewrite", UrlRewrite.class);
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        SSLContextParameters ssl = sslContextParameters != null ? sslContextParameters : this.sslContextParameters;
        ssl = ssl != null ? ssl : retrieveGlobalSslContextParameters();
        String proxyHost = getAndRemoveParameter(parameters, "proxyHost", String.class, getProxyHost());
        Integer proxyPort = getAndRemoveParameter(parameters, "proxyPort", Integer.class, getProxyPort());
        Integer httpClientMinThreads = getAndRemoveParameter(parameters, "httpClientMinThreads", Integer.class, this.httpClientMinThreads);
        Integer httpClientMaxThreads = getAndRemoveParameter(parameters, "httpClientMaxThreads", Integer.class, this.httpClientMaxThreads);
        HttpClient httpClient = resolveAndRemoveReferenceParameter(parameters, "httpClient", HttpClient.class);
        Boolean async = getAndRemoveParameter(parameters, "async", Boolean.class);

        // extract httpClient. parameters
        Map<String, Object> httpClientParameters = IntrospectionSupport.extractProperties(parameters, "httpClient.");

        // extract filterInit. parameters
        Map<String, String> filterInitParameters =  IntrospectionSupport.extractStringProperties(IntrospectionSupport.extractProperties(parameters, "filterInit."));

        String address = remaining;
        URI addressUri = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(address));
        URI endpointUri = URISupport.createRemainingURI(addressUri, parameters);
        // need to keep the httpMethodRestrict parameter for the endpointUri
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(addressUri, parameters);
        // create endpoint after all known parameters have been extracted from parameters

        // include component scheme in the uri
        String scheme = ObjectHelper.before(uri, ":");
        endpointUri = new URI(scheme + ":" + endpointUri);

        JettyHttpEndpoint endpoint = createEndpoint(endpointUri, httpUri);
        if (async != null) {
            endpoint.setAsync(async);
        }

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
        if (filterInitParameters != null && !filterInitParameters.isEmpty()) {
            endpoint.setFilterInitParameters(filterInitParameters);
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
                filters = new ArrayList<Filter>(1);
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
        if (httpClientMinThreads != null) {
            endpoint.setHttpClientMinThreads(httpClientMinThreads);
        }
        if (httpClientMaxThreads != null) {
            endpoint.setHttpClientMaxThreads(httpClientMaxThreads);
        }
        if (httpClient != null) {
            endpoint.setHttpClient(httpClient);
        }
        endpoint.setSendServerVersion(isSendServerVersion());

        setProperties(endpoint, parameters);

        // re-create http uri after all parameters has been set on the endpoint, as the remainders are for http uri
        httpUri = URISupport.createRemainingURI(addressUri, parameters);
        endpoint.setHttpUri(httpUri);

        return endpoint;
    }

    protected abstract JettyHttpEndpoint createEndpoint(URI endpointUri, URI httpUri) throws URISyntaxException;

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
                Server server = createServer();
                Connector connector = getConnector(server, endpoint);
                if ("localhost".equalsIgnoreCase(endpoint.getHttpUri().getHost())) {
                    LOG.warn("You use localhost interface! It means that no external connections will be available."
                            + " Don't you want to use 0.0.0.0 instead (all network interfaces)? " + endpoint);
                }
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
            addServerMBean(server);
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
            if (endpoint.getFilterInitParameters() != null) {
                filterHolder.setInitParameters(endpoint.getFilterInitParameters());
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
        }
    }

    private void addFilter(ServletContextHandler context, FilterHolder filterHolder, String pathSpec) {
        context.getServletHandler().addFilterWithMapping(filterHolder, pathSpec, 0);
    }

    private void enableMultipartFilter(HttpCommonEndpoint endpoint, Server server, String connectorKey) throws Exception {
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
     * The key password, which is used to access the certificate's key entry in the keystore (this is the same password that is supplied to the keystore command's -keypass option).
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
     * The ssl password, which is required to access the keystore file (this is the same password that is supplied to the keystore command's -storepass option).
     */
    @Metadata(description = "The ssl password, which is required to access the keystore file (this is the same password that is supplied to the keystore command's -storepass option).",
            label = "security", secret = true)
    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }

    /**
     * Specifies the location of the Java keystore file, which contains the Jetty server's own X.509 certificate in a key entry.
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
        SslContextFactory sslcf = null;

        // Note that this was set on the endpoint when it was constructed.  It was
        // either explicitly set at the component or on the endpoint, but either way,
        // the value is already set.  We therefore do not need to look at the component
        // level SSLContextParameters again in this method.
        SSLContextParameters endpointSslContextParameters = endpoint.getSslContextParameters();

        if (endpointSslContextParameters != null) {
            try {
                sslcf = createSslContextFactory(endpointSslContextParameters);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else if ("https".equals(endpoint.getProtocol())) {
            sslcf = new SslContextFactory();
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

    protected abstract AbstractConnector createConnectorJettyInternal(Server server, JettyHttpEndpoint endpoint, SslContextFactory sslcf);

    private SslContextFactory createSslContextFactory(SSLContextParameters ssl) throws GeneralSecurityException, IOException {
        SslContextFactory answer = new SslContextFactory();
        if (ssl != null) {
            answer.setSslContext(ssl.createSSLContext(getCamelContext()));
        }

        // jetty default is
        // addExcludeProtocols("SSL", "SSLv2", "SSLv2Hello", "SSLv3");
        // setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$");

        // configure include/exclude ciphers and protocols
        if (ssl != null && ssl.getCipherSuitesFilter() != null)  {
            List<String> includeCiphers = ssl.getCipherSuitesFilter().getInclude();
            if (includeCiphers != null && !includeCiphers.isEmpty()) {
                String[] arr = includeCiphers.toArray(new String[includeCiphers.size()]);
                answer.setIncludeCipherSuites(arr);
            } else {
                answer.setIncludeCipherSuites(".*");
            }
            List<String> excludeCiphers = ssl.getCipherSuitesFilter().getExclude();
            if (excludeCiphers != null && !excludeCiphers.isEmpty()) {
                String[] arr = excludeCiphers.toArray(new String[excludeCiphers.size()]);
                answer.setExcludeCipherSuites(arr);
            }
        }
        if (ssl != null && ssl.getSecureSocketProtocolsFilter() != null) {
            List<String> includeProtocols = ssl.getSecureSocketProtocolsFilter().getInclude();
            if (includeProtocols != null && !includeProtocols.isEmpty()) {
                String[] arr = includeProtocols.toArray(new String[includeProtocols.size()]);
                answer.setIncludeProtocols(arr);
            } else {
                answer.setIncludeProtocols(".*");
            }
            List<String> excludeProtocols = ssl.getSecureSocketProtocolsFilter().getExclude();
            if (excludeProtocols != null && !excludeProtocols.isEmpty()) {
                String[] arr = excludeProtocols.toArray(new String[excludeProtocols.size()]);
                answer.setExcludeProtocols(arr);
            }
        }

        return answer;
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

    public Map<Integer, Connector> getSslSocketConnectors() {
        return sslSocketConnectors;
    }

    /**
     * A map which contains per port number specific SSL connectors.
     */
    @Metadata(description = "A map which contains per port number specific SSL connectors.", label = "security")
    public void setSslSocketConnectors(Map <Integer, Connector> connectors) {
        sslSocketConnectors = connectors;
    }

    /**
     * A map which contains per port number specific HTTP connectors. Uses the same principle as sslSocketConnectors.
     */
    @Metadata(description = "A map which contains per port number specific HTTP connectors. Uses the same principle as sslSocketConnectors.", label = "security")
    public void setSocketConnectors(Map<Integer, Connector> socketConnectors) {
        this.socketConnectors = socketConnectors;
    }

    /**
     * Creates a new {@link HttpClient} and configures its proxy/thread pool and SSL based on this
     * component settings.
     *
     * @param endpoint   the instance of JettyHttpEndpoint
     * @param minThreads optional minimum number of threads in client thread pool
     * @param maxThreads optional maximum number of threads in client thread pool
     * @param ssl        option SSL parameters
     */
    public CamelHttpClient createHttpClient(JettyHttpEndpoint endpoint, Integer minThreads, Integer maxThreads, SSLContextParameters ssl) throws Exception {
        SslContextFactory sslContextFactory = createSslContextFactory(ssl);
        HttpClientTransport transport = createHttpClientTransport(maxThreads);
        CamelHttpClient httpClient = createCamelHttpClient(transport, sslContextFactory);

        CamelContext context = endpoint.getCamelContext();

        if (context != null
            && ObjectHelper.isNotEmpty(context.getProperty("http.proxyHost"))
            && ObjectHelper.isNotEmpty(context.getProperty("http.proxyPort"))) {
            String host = context.getProperty("http.proxyHost");
            int port = Integer.parseInt(context.getProperty("http.proxyPort"));
            LOG.debug("CamelContext properties http.proxyHost and http.proxyPort detected. Using http proxy host: {} port: {}", host, port);
            httpClient.setProxy(host, port);
        }

        if (ObjectHelper.isNotEmpty(endpoint.getProxyHost()) && endpoint.getProxyPort() > 0) {
            String host = endpoint.getProxyHost();
            int port = endpoint.getProxyPort();
            LOG.debug("proxyHost and proxyPort options detected. Using http proxy host: {} port: {}", host, port);
            httpClient.setProxy(host, port);
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
            httpClient.setThreadPoolOrExecutor(qtp);
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

    private HttpClientTransport createHttpClientTransport(Integer maxThreads) {
        if (maxThreads == null) {
            return new HttpClientTransportOverHTTP();
        }

        int selectors = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

        if (selectors >= maxThreads) {
            selectors = maxThreads - 1;
        }

        return new HttpClientTransportOverHTTP(selectors);
    }

    protected abstract CamelHttpClient createCamelHttpClient(HttpClientTransport transport, SslContextFactory sslContextFactory);

    public Integer getHttpClientMinThreads() {
        return httpClientMinThreads;
    }

    /**
     * To set a value for minimum number of threads in HttpClient thread pool. Notice that both a min and max size must be configured.
     */
    @Metadata(description = "To set a value for minimum number of threads in HttpClient thread pool. Notice that both a min and max size must be configured.", label = "producer")
    public void setHttpClientMinThreads(Integer httpClientMinThreads) {
        this.httpClientMinThreads = httpClientMinThreads;
    }

    public Integer getHttpClientMaxThreads() {
        return httpClientMaxThreads;
    }

    /**
     * To set a value for maximum number of threads in HttpClient thread pool. Notice that both a min and max size must be configured.
     */
    @Metadata(description = "To set a value for maximum number of threads in HttpClient thread pool. Notice that both a min and max size must be configured.", label = "producer")
    public void setHttpClientMaxThreads(Integer httpClientMaxThreads) {
        this.httpClientMaxThreads = httpClientMaxThreads;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    /**
     * To set a value for minimum number of threads in server thread pool. Notice that both a min and max size must be configured.
     */
    @Metadata(description = "To set a value for minimum number of threads in server thread pool. Notice that both a min and max size must be configured.", label = "consumer")
    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    /**
     * To set a value for maximum number of threads in server thread pool. Notice that both a min and max size must be configured.
     */
    @Metadata(description = "To set a value for maximum number of threads in server thread pool. Notice that both a min and max size must be configured.", label = "consumer")
    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    /**
     * To use a custom thread pool for the server. This option should only be used in special circumstances.
     */
    @Metadata(description = "To use a custom thread pool for the server. This option should only be used in special circumstances.", label = "consumer,advanced")
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

    public JettyHttpBinding getJettyHttpBinding() {
        return jettyHttpBinding;
    }

    /**
     * To use a custom org.apache.camel.component.jetty.JettyHttpBinding, which are used to customize how a response should be written for the producer.
     */
    @Metadata(description = "To use a custom org.apache.camel.component.jetty.JettyHttpBinding, which are used to customize how a response should be written for the producer.", label = "advanced")
    public void setJettyHttpBinding(JettyHttpBinding jettyHttpBinding) {
        this.jettyHttpBinding = jettyHttpBinding;
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
                startMbContainer();
            } else {
                LOG.warn("JMX disabled in CamelContext. Jetty JMX extensions will remain disabled.");
            }
        }

        return this.mbContainer;
    }

    /**
     * To use a existing configured org.eclipse.jetty.jmx.MBeanContainer if JMX is enabled that Jetty uses for registering mbeans.
     */
    @Metadata(description = "To use a existing configured org.eclipse.jetty.jmx.MBeanContainer if JMX is enabled that Jetty uses for registering mbeans.", label = "advanced")
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
    @Metadata(description = "A map which contains general HTTP connector properties. Uses the same principle as sslSocketConnectorProperties.", label = "security")
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

    /**
     * Allows to set a timeout in millis when using Jetty as consumer (server).
     * By default Jetty uses 30000. You can use a value of <= 0 to never expire.
     * If a timeout occurs then the request will be expired and Jetty will return back a http error 503 to the client.
     * This option is only in use when using Jetty with the Asynchronous Routing Engine.
     */
    @Metadata(description = "Allows to set a timeout in millis when using Jetty as consumer (server)."
            + " By default Jetty uses 30000. You can use a value of <= 0 to never expire."
            + " If a timeout occurs then the request will be expired and Jetty will return back a http error 503 to the client."
            + " This option is only in use when using Jetty with the Asynchronous Routing Engine.", defaultValue = "30000", label = "consumer")
    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public boolean isUseContinuation() {
        return useContinuation;
    }

    /**
     * Whether or not to use Jetty continuations for the Jetty Server.
     */
    @Metadata(description = "Whether or not to use Jetty continuations for the Jetty Server.", defaultValue = "true", label = "consumer")
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
     * If the option is true, jetty will send the server header with the jetty version information to the client which sends the request.
     * NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.
     */
    @Metadata(description = "If the option is true, jetty server will send the date header to the client which sends the request."
            + " NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.",
            defaultValue = "true", label = "consumer")
    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

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
            config = camelContext.getRestConfiguration("jetty", true);
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
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                host = "0.0.0.0";
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("jetty")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        boolean cors = config.isEnableCORS();
        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
        }

        String query = URISupport.createQueryString(map);

        String url;
        if (api) {
            url = "jetty:%s://%s:%s/%s?matchOnUriPrefix=true&httpMethodRestrict=%s";
        } else {
            url = "jetty:%s://%s:%s/%s?httpMethodRestrict=%s";
        }

        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);
        if (cors) {
            restrict += ",OPTIONS";
        }
        // get the endpoint
        url = String.format(url, scheme, host, port, path, restrict);

        if (!query.isEmpty()) {
            url = url + "&" + query;
        }

        JettyHttpEndpoint endpoint = camelContext.getEndpoint(url, JettyHttpEndpoint.class);
        setProperties(camelContext, endpoint, parameters);

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

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String url = "jetty:" + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        JettyHttpEndpoint endpoint = camelContext.getEndpoint(url, JettyHttpEndpoint.class);
        if (parameters != null && !parameters.isEmpty()) {
            setProperties(camelContext, endpoint, parameters);
        }
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.setHeaderFilterStrategy(new HttpRestHeaderFilterStrategy(path, queryParameters));

        return endpoint.createProducer();
    }

    protected CamelServlet createServletForConnector(Server server, Connector connector,
                                                     List<Handler> handlers, JettyHttpEndpoint endpoint) throws Exception {
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        if (Server.getVersion().startsWith("8")) {
            context.getClass().getMethod("setConnectorNames", new Class[] {String[].class})
                .invoke(context, new Object[] {new String[] {connector.getName()}});
        }

        addJettyHandlers(server, handlers);

        CamelServlet camelServlet = new CamelContinuationServlet();
        ServletHolder holder = new ServletHolder();
        holder.setServlet(camelServlet);
        holder.setAsyncSupported(true);
        holder.setInitParameter(CamelServlet.ASYNC_PARAM, Boolean.toString(endpoint.isAsync()));
        context.addServlet(holder, "/*");

        // use rest enabled resolver in case we use rest
        camelServlet.setServletResolveConsumerStrategy(new HttpRestServletResolveConsumerStrategy());

        return camelServlet;
    }

    protected void addJettyHandlers(Server server, List<Handler> handlers) {
        if (handlers != null && !handlers.isEmpty()) {
            for (Handler handler : handlers) {
                if (handler instanceof HandlerWrapper) {
                    // avoid setting a handler more than once
                    if (!isHandlerInChain(server.getHandler(), handler)) {
                        ((HandlerWrapper) handler).setHandler(server.getHandler());
                        server.setHandler(handler);
                    }
                } else {
                    HandlerCollection handlerCollection = new HandlerCollection();
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
        } else if (current instanceof HandlerWrapper) {
            //Inspect the next handler in the chain
            return isHandlerInChain(((HandlerWrapper) current).getHandler(), handler);
        } else {
            //End of chain
            return false;
        }
    }

    protected Server createServer() {
        Server s = null;
        ThreadPool tp = threadPool;
        QueuedThreadPool qtp = null;
        // configure thread pool if min/max given
        if (minThreads != null || maxThreads != null) {
            if (getThreadPool() != null) {
                throw new IllegalArgumentException("You cannot configure both minThreads/maxThreads and a custom threadPool on JettyHttpComponent: " + this);
            }
            qtp = new QueuedThreadPool();
            if (minThreads != null) {
                qtp.setMinThreads(minThreads.intValue());
            }
            if (maxThreads != null) {
                qtp.setMaxThreads(maxThreads.intValue());
            }
            tp = qtp;
        }
        if (tp != null) {
            try {
                if (!Server.getVersion().startsWith("8")) {
                    s = Server.class.getConstructor(ThreadPool.class).newInstance(tp);

                } else {
                    s = new Server();
                    if (isEnableJmx()) {
                        enableJmx(s);
                    }
                    Server.class.getMethod("setThreadPool", ThreadPool.class).invoke(s, tp);
                }
            } catch (Exception e) {
                //ignore
            }
        }
        if (s == null) {
            s = new Server();
        }
        if (qtp != null) {
            // let the thread names indicate they are from the server
            qtp.setName("CamelJettyServer(" + ObjectHelper.getIdentityHashCode(s) + ")");
            try {
                qtp.start();
            } catch (Exception e) {
                throw new RuntimeCamelException("Error starting JettyServer thread pool: " + qtp, e);
            }
        }
        ContextHandlerCollection collection = new ContextHandlerCollection();
        s.setHandler(collection);
        // setup the error handler if it set to Jetty component
        if (getErrorHandler() != null) {
            s.addBean(getErrorHandler());
        } else if (!Server.getVersion().startsWith("8")) {
            //need an error handler that won't leak information about the exception 
            //back to the client.
            ErrorHandler eh = new ErrorHandler() {
                public void handle(String target, Request baseRequest,
                                   HttpServletRequest request, HttpServletResponse response)
                    throws IOException {
                    String msg = HttpStatus.getMessage(response.getStatus());
                    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, msg);
                    if (response instanceof Response) {
                        //need to use the deprecated method to support compiling with Jetty 8
                        ((Response)response).setStatus(response.getStatus(), msg);
                    }
                    super.handle(target, baseRequest, request, response);
                }
                protected void writeErrorPage(HttpServletRequest request, Writer writer, int code,
                                              String message, boolean showStacks)
                    throws IOException {
                    super.writeErrorPage(request, writer, code, message, false);
                }
            };
            s.addBean(eh, false);
        }
        return s;
    }


    /**
     * Starts {@link #mbContainer} and registers the container with itself as a managed bean
     * logging an error if there is a problem starting the container.
     * Does nothing if {@link #mbContainer} is {@code null}.
     */
    protected void startMbContainer() {
        if (mbContainer != null
            && Server.getVersion().startsWith("8")) {
            //JETTY8 only
            try {
                boolean b = (Boolean)mbContainer.getClass().getMethod("isStarted").invoke(mbContainer);
                if (b) {
                    mbContainer.getClass().getMethod("start").invoke(mbContainer);
                    // Publish the container itself for consistency with
                    // traditional embedded Jetty configurations.
                    mbContainer.getClass().getMethod("addBean", Object.class).invoke(mbContainer, mbContainer);
                }
            } catch (Throwable e) {
                LOG.warn("Could not start Jetty MBeanContainer. Jetty JMX extensions will remain disabled.", e);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        RestConfiguration config = getCamelContext().getRestConfiguration("jetty", true);
        // configure additional options on jetty configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(this, config.getComponentProperties());
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
                    // Camel controls the lifecycle of these entities so remove the
                    // registered MBeans when Camel is done with the managed objects.
                    removeServerMBean(connectorRef.server);
                    connectorRef.server.stop();
                    //removeServerMBean(connectorRef.connector);
                    CONNECTORS.remove(connectorKey);
                }
            }
        }
        if (mbContainer != null) {
            try {
                //JETTY8
                mbContainer.getClass().getMethod("stop").invoke(mbContainer);
            } catch (Throwable t) {
                //JETTY9
                mbContainer.getClass().getMethod("destroy").invoke(mbContainer);
            }
            mbContainer = null;
        }
    }

    private void addServerMBean(Server server) {
        if (mbContainer == null) {
            return;
        }

        try {
            Object o = getContainer(server);
            o.getClass().getMethod("addEventListener", Container.Listener.class).invoke(o, mbContainer);
            if (Server.getVersion().startsWith("8")) {
                return;
            }
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
            return (Container)server;
        }
        try {
            return (Container)server.getClass().getMethod("getContainer").invoke(server);
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
