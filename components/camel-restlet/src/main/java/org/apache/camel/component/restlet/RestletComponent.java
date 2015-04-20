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
package org.apache.camel.component.restlet;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.MapVerifier;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel component embedded Restlet that produces and consumes exchanges.
 * 
 * @version
 */
public class RestletComponent extends HeaderFilterStrategyComponent implements RestConsumerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(RestletComponent.class);

    private final Map<String, Server> servers = new HashMap<String, Server>();
    private final Map<String, MethodBasedRouter> routers = new HashMap<String, MethodBasedRouter>();
    private final Component component;

    // options that can be set on the restlet server
    private Boolean controllerDaemon;
    private Integer controllerSleepTimeMs;
    private Integer inboundBufferSize;
    private Integer minThreads;
    private Integer maxThreads;
    private Integer lowThreads;
    private Integer maxConnectionsPerHost;
    private Integer maxTotalConnections;
    private Integer outboundBufferSize;
    private Integer maxQueued;
    private Boolean persistingConnections;
    private Boolean pipeliningConnections;
    private Integer threadMaxIdleTimeMs;
    private Boolean useForwardedForHeader;
    private Boolean reuseAddress;
    private boolean disableStreamCache;
    private int port;
    private Boolean synchronous;

    public RestletComponent() {
        this(new Component());
    }

    public RestletComponent(Component component) {
        // Allow the Component to be injected, so that the RestletServlet may be
        // configured within a webapp
        super(RestletEndpoint.class);
        this.component = component;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RestletEndpoint result = new RestletEndpoint(this, remaining);
        if (synchronous != null) {
            result.setSynchronous(synchronous);
        }
        result.setDisableStreamCache(isDisableStreamCache());
        setEndpointHeaderFilterStrategy(result);
        setProperties(result, parameters);
        // set the endpoint uri according to the parameter
        result.updateEndpointUri();

        // construct URI so we can use it to get the splitted information
        URI u = new URI(remaining);
        String protocol = u.getScheme();

        String uriPattern = u.getPath();
        if (parameters.size() > 0) {
            uriPattern = uriPattern + "?" + URISupport.createQueryString(parameters);
        }

        int port = 0;
        String host = u.getHost();
        if (u.getPort() > 0) {
            port = u.getPort();
        } else {
            port = this.port;
        }

        result.setProtocol(protocol);
        result.setUriPattern(uriPattern);
        result.setHost(host);
        if (port > 0) {
            result.setPort(port);
        }

        return result;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // configure component options
        RestConfiguration config = getCamelContext().getRestConfiguration();
        if (config != null && (config.getComponent() == null || config.getComponent().equals("restlet"))) {
            // configure additional options on spark configuration
            if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
                setProperties(this, config.getComponentProperties());
            }
        }

        component.start();
    }

    @Override
    protected void doStop() throws Exception {
        component.stop();
        // component stop will stop servers so we should clear our list as well
        servers.clear();
        // routers map entries are removed as consumer stops and servers map
        // is not touch so to keep in sync with component's servers
        super.doStop();
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        // we invoke setProperties ourselves so we can construct "user" uri on
        // on the remaining parameters
        return false;
    }

    public void connect(RestletConsumer consumer) throws Exception {
        RestletEndpoint endpoint = consumer.getEndpoint();
        addServerIfNecessary(endpoint);

        // if restlet servlet server is created, the offsetPath is set in component context
        // see http://restlet.tigris.org/issues/show_bug.cgi?id=988
        String offsetPath = (String) this.component.getContext()
                .getAttributes().get("org.restlet.ext.servlet.offsetPath");
        
        if (endpoint.getUriPattern() != null && endpoint.getUriPattern().length() > 0) {
            attachUriPatternToRestlet(offsetPath, endpoint.getUriPattern(), endpoint, consumer.getRestlet());
        }

        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                attachUriPatternToRestlet(offsetPath, uriPattern, endpoint, consumer.getRestlet());
            }
        }
    }

    public void disconnect(RestletConsumer consumer) throws Exception {
        RestletEndpoint endpoint = consumer.getEndpoint();

        List<MethodBasedRouter> routesToRemove = new ArrayList<MethodBasedRouter>();

        String pattern = decodePattern(endpoint.getUriPattern());
        if (pattern != null && !pattern.isEmpty()) {
            routesToRemove.add(getMethodRouter(pattern, false));
        }

        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                routesToRemove.add(getMethodRouter(uriPattern, false));
            }
        }

        for (MethodBasedRouter router : routesToRemove) {
            if (endpoint.getRestletMethods() != null) {
                Method[] methods = endpoint.getRestletMethods();
                for (Method method : methods) {
                    router.removeRoute(method);
                }
            } else {
                router.removeRoute(endpoint.getRestletMethod());
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Detached restlet uriPattern: {} method: {}", router.getUriPattern(),
                          endpoint.getRestletMethod());
            }

            // remove router if its no longer in use
            if (!router.hasRoutes()) {
                deattachUriPatternFrimRestlet(router.getUriPattern(), endpoint, router);
                if (!router.isStopped()) {
                    router.stop();
                }
                routers.remove(router.getUriPattern());
            }
        }
    }

    private MethodBasedRouter getMethodRouter(String uriPattern, boolean addIfEmpty) {
        synchronized (routers) {
            MethodBasedRouter result = routers.get(uriPattern);
            if (result == null && addIfEmpty) {
                result = new MethodBasedRouter(uriPattern);
                LOG.debug("Added method based router: {}", result);
                routers.put(uriPattern, result);
            }
            return result;
        }
    }
    
    protected Server createServer(RestletEndpoint endpoint) {
        return new Server(component.getContext().createChildContext(), Protocol.valueOf(endpoint.getProtocol()), endpoint.getPort());
    }
    
    protected String stringArrayToString(String[] strings) {
        StringBuffer result = new StringBuffer();
        for (String str : strings) {
            result.append(str);
            result.append(" ");
        }
        return result.toString();
    }
    
    protected void setupServerWithSSLContext(Series<Parameter> params, SSLContextParameters scp) throws GeneralSecurityException, IOException {
        // set the SSLContext parameters
        params.add("sslContextFactory",
            "org.restlet.engine.ssl.DefaultSslContextFactory");
        
        SSLContext context = scp.createSSLContext();
        SSLEngine engine = context.createSSLEngine();
        
        params.add("enabledProtocols", stringArrayToString(engine.getEnabledProtocols()));
        params.add("enabledCipherSuites", stringArrayToString(engine.getEnabledCipherSuites()));
        
        if (scp.getSecureSocketProtocol() != null) {
            params.add("protocol", scp.getSecureSocketProtocol());
        }
        if (scp.getServerParameters() != null && scp.getServerParameters().getClientAuthentication() != null) {
            boolean b = !scp.getServerParameters().getClientAuthentication().equals("NONE");
            params.add("needClientAuthentication", String.valueOf(b));
        }
        if (scp.getKeyManagers() != null) { 
            if (scp.getKeyManagers().getAlgorithm() != null) {
                params.add("keyManagerAlgorithm", scp.getKeyManagers().getAlgorithm());
            }
            if (scp.getKeyManagers().getKeyPassword() != null) {
                params.add("keyPassword", scp.getKeyManagers().getKeyPassword());
            }
            if (scp.getKeyManagers().getKeyStore().getResource() != null) {
                params.add("keyStorePath", scp.getKeyManagers().getKeyStore().getResource());
            }
            if (scp.getKeyManagers().getKeyStore().getPassword() != null) {
                params.add("keyStorePassword", scp.getKeyManagers().getKeyStore().getPassword());
            }
            if (scp.getKeyManagers().getKeyStore().getType() != null) {
                params.add("keyStoreType", scp.getKeyManagers().getKeyStore().getType());
            }
        }
        
        if (scp.getTrustManagers() != null) { 
            if (scp.getTrustManagers().getAlgorithm() != null) {
                params.add("trustManagerAlgorithm", scp.getKeyManagers().getAlgorithm());
            }
            if (scp.getTrustManagers().getKeyStore().getResource() != null) {
                params.add("trustStorePath", scp.getTrustManagers().getKeyStore().getResource());
            }
            if (scp.getTrustManagers().getKeyStore().getPassword() != null) {
                params.add("trustStorePassword", scp.getTrustManagers().getKeyStore().getPassword());
            }
            if (scp.getTrustManagers().getKeyStore().getType() != null) {
                params.add("trustStoreType", scp.getTrustManagers().getKeyStore().getType());
            }
        }
    }

    protected void addServerIfNecessary(RestletEndpoint endpoint) throws Exception {
        String key = buildKey(endpoint);
        Server server;
        synchronized (servers) {
            server = servers.get(key);
            if (server == null) {
                server = createServer(endpoint);
                component.getServers().add(server);

                // Add any Restlet server parameters that were included
                Series<Parameter> params = server.getContext().getParameters();
                
                if ("https".equals(endpoint.getProtocol())) {
                    SSLContextParameters scp = endpoint.getSslContextParameters();
                    if (endpoint.getSslContextParameters() == null) {
                        throw new InvalidParameterException("Need to specify the SSLContextParameters option here!");
                    }
                    setupServerWithSSLContext(params, scp);
                }

                if (getControllerDaemon() != null) {
                    params.add("controllerDaemon", getControllerDaemon().toString());
                }
                if (getControllerSleepTimeMs() != null) {
                    params.add("controllerSleepTimeMs", getControllerSleepTimeMs().toString());
                }
                if (getInboundBufferSize() != null) {
                    params.add("inboundBufferSize", getInboundBufferSize().toString());
                }
                if (getMinThreads() != null) {
                    params.add("minThreads", getMinThreads().toString());
                }
                if (getMaxThreads() != null) {
                    params.add("maxThreads", getMaxThreads().toString());
                }
                if (getLowThreads() != null) {
                    params.add("lowThreads", getLowThreads().toString());
                }
                if (getMaxQueued() != null) {
                    params.add("maxQueued", getMaxQueued().toString());
                }
                if (getMaxConnectionsPerHost() != null) {
                    params.add("maxConnectionsPerHost", getMaxConnectionsPerHost().toString());
                }
                if (getMaxTotalConnections() != null) {
                    params.add("maxTotalConnections", getMaxTotalConnections().toString());
                }
                if (getOutboundBufferSize() != null) {
                    params.add("outboundBufferSize", getOutboundBufferSize().toString());
                }
                if (getPersistingConnections() != null) {
                    params.add("persistingConnections", getPersistingConnections().toString());
                }
                if (getPipeliningConnections() != null) {
                    params.add("pipeliningConnections", getPipeliningConnections().toString());
                }
                if (getThreadMaxIdleTimeMs() != null) {
                    params.add("threadMaxIdleTimeMs", getThreadMaxIdleTimeMs().toString());
                }
                if (getUseForwardedForHeader() != null) {
                    params.add("useForwardedForHeader", getUseForwardedForHeader().toString());
                }
                if (getReuseAddress() != null) {
                    params.add("reuseAddress", getReuseAddress().toString());
                }
                
                LOG.debug("Setting parameters: {} to server: {}", params, server);
                server.getContext().setParameters(params);

                servers.put(key, server);
                LOG.debug("Added server: {}", key);
                server.start();
            }
        }
    }

    private static String buildKey(RestletEndpoint endpoint) {
        return endpoint.getHost() + ":" + endpoint.getPort();
    }

    private void attachUriPatternToRestlet(String offsetPath, String uriPattern, RestletEndpoint endpoint, Restlet target) throws Exception {
        uriPattern = decodePattern(uriPattern);
        MethodBasedRouter router = getMethodRouter(uriPattern, true);

        Map<String, String> realm = endpoint.getRestletRealm();
        if (realm != null && realm.size() > 0) {
            ChallengeAuthenticator guard = new ChallengeAuthenticator(component.getContext()
                .createChildContext(), ChallengeScheme.HTTP_BASIC, "Camel-Restlet Endpoint Realm");
            MapVerifier verifier = new MapVerifier();
            for (Map.Entry<String, String> entry : realm.entrySet()) {
                verifier.getLocalSecrets().put(entry.getKey(), entry.getValue().toCharArray());
            }
            guard.setVerifier(verifier);
            guard.setNext(target);
            target = guard;
            LOG.debug("Target has been set to guard: {}", guard);
        }

        if (endpoint.getRestletMethods() != null) {
            Method[] methods = endpoint.getRestletMethods();
            for (Method method : methods) {
                router.addRoute(method, target);
                LOG.debug("Attached restlet uriPattern: {} method: {}", uriPattern, method);
            }
        } else {
            Method method = endpoint.getRestletMethod();
            router.addRoute(method, target);
            LOG.debug("Attached restlet uriPattern: {} method: {}", uriPattern, method);
        }

        if (!router.hasBeenAttached()) {
            component.getDefaultHost().attach(
                    offsetPath == null ? uriPattern : offsetPath + uriPattern, router);
            LOG.debug("Attached methodRouter uriPattern: {}", uriPattern);
        }

        if (!router.isStarted()) {
            router.start();
            LOG.debug("Started methodRouter uriPattern: {}", uriPattern);
        }
    }

    private void deattachUriPatternFrimRestlet(String uriPattern, RestletEndpoint endpoint, Restlet target) throws Exception {
        component.getDefaultHost().detach(target);
        LOG.debug("Deattached methodRouter uriPattern: {}", uriPattern);
    }

    @Deprecated
    protected String preProcessUri(String uri) {
        // If the URI was not valid (i.e. contains '{' and '}'
        // it was most likely encoded by normalizeEndpointUri in DefaultCamelContext.getEndpoint(String)
        return UnsafeUriCharactersEncoder.encode(uri.replaceAll("%7B", "(").replaceAll("%7D", ")"));
    }
    
    private static String decodePattern(String pattern) {
        return pattern == null ? null : pattern.replaceAll("\\(", "{").replaceAll("\\)", "}");
    }

    public Boolean getControllerDaemon() {
        return controllerDaemon;
    }

    public void setControllerDaemon(Boolean controllerDaemon) {
        this.controllerDaemon = controllerDaemon;
    }

    public Integer getControllerSleepTimeMs() {
        return controllerSleepTimeMs;
    }

    public void setControllerSleepTimeMs(Integer controllerSleepTimeMs) {
        this.controllerSleepTimeMs = controllerSleepTimeMs;
    }

    public Integer getInboundBufferSize() {
        return inboundBufferSize;
    }

    public void setInboundBufferSize(Integer inboundBufferSize) {
        this.inboundBufferSize = inboundBufferSize;
    }

    public Integer getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public void setMaxConnectionsPerHost(Integer maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }
    
    public Integer getLowThreads() {
        return lowThreads;
    }

    public void setLowThreads(Integer lowThreads) {
        this.lowThreads = lowThreads;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getOutboundBufferSize() {
        return outboundBufferSize;
    }

    public void setOutboundBufferSize(Integer outboundBufferSize) {
        this.outboundBufferSize = outboundBufferSize;
    }

    public Boolean getPersistingConnections() {
        return persistingConnections;
    }

    public void setPersistingConnections(Boolean persistingConnections) {
        this.persistingConnections = persistingConnections;
    }

    public Boolean getPipeliningConnections() {
        return pipeliningConnections;
    }

    public void setPipeliningConnections(Boolean pipeliningConnections) {
        this.pipeliningConnections = pipeliningConnections;
    }

    public Integer getThreadMaxIdleTimeMs() {
        return threadMaxIdleTimeMs;
    }

    public void setThreadMaxIdleTimeMs(Integer threadMaxIdleTimeMs) {
        this.threadMaxIdleTimeMs = threadMaxIdleTimeMs;
    }

    public Boolean getUseForwardedForHeader() {
        return useForwardedForHeader;
    }

    public void setUseForwardedForHeader(Boolean useForwardedForHeader) {
        this.useForwardedForHeader = useForwardedForHeader;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public Integer getMaxQueued() {
        return maxQueued;
    }

    public void setMaxQueued(Integer maxQueued) {
        this.maxQueued = maxQueued;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Boolean getSynchronous() {
        return synchronous;
    }

    public void setSynchronous(Boolean synchronous) {
        this.synchronous = synchronous;
    }

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
        // use the component's port as the default value
        int port = this.getPort();

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = getCamelContext().getRestConfiguration();
        if (config.getComponent() == null || config.getComponent().equals("restlet")) {
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
        if (config != null && (config.getComponent() == null || config.getComponent().equals("restlet"))) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url;
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);

        if (port > 0) {
            url = "restlet:%s://%s:%s/%s?restletMethod=%s";
            url = String.format(url, scheme, host, port, path, restrict);
        } else {
            // It could use the restlet servlet transport
            url = "restlet:/%s?restletMethod=%s";
            url = String.format(url, path, restrict);
        }
        if (!query.isEmpty()) {
            url = url + "&" + query;
        }
        // get the endpoint
        RestletEndpoint endpoint = camelContext.getEndpoint(url, RestletEndpoint.class);
        setProperties(endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config != null && config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }
}
