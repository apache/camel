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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Component;
import org.restlet.Guard;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Protocol;

/**
 * A Camel component embedded Restlet that produces and consumes exchanges.
 *
 * @version $Revision$
 */
public class RestletComponent extends HeaderFilterStrategyComponent {
    private static final Log LOG = LogFactory.getLog(RestletComponent.class);

    private final Map<String, Server> servers = new HashMap<String, Server>();
    private final Map<String, MethodBasedRouter> routers = new HashMap<String, MethodBasedRouter>();
    private final Component component = new Component();
    
    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        
        RestletEndpoint result = new RestletEndpoint(this, remaining);
        setEndpointHeaderFilterStrategy(result);
        setProperties(result, parameters);
        
        // construct URI so we can use it to get the splitted information
        URI u = new URI(UnsafeUriCharactersEncoder.encode(remaining));
        String protocol = u.getScheme();

        String uriPattern = u.getPath();
        if (parameters.size() > 0) {
            uriPattern = uriPattern + "?" + URISupport.createQueryString(parameters);
        }

        int port = 0;
        String host = u.getHost();
        if (u.getPort() > 0) {
            port = u.getPort();
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
        component.start();
    }
    
    @Override
    protected void doStop() throws Exception {
        component.stop();
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
        RestletEndpoint endpoint = (RestletEndpoint)consumer.getEndpoint();
        addServerIfNecessary(endpoint);
        
        if (endpoint.getUriPattern() != null && endpoint.getUriPattern().length() > 0) {
            attachUriPatternToRestlet(endpoint.getUriPattern(), endpoint, consumer.getRestlet());
        }
        
        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                attachUriPatternToRestlet(uriPattern, endpoint, consumer.getRestlet());
            }
        }
    }

    public void disconnect(RestletConsumer consumer) throws Exception {
        RestletEndpoint endpoint = (RestletEndpoint)consumer.getEndpoint();
        
        List<MethodBasedRouter> routers = new ArrayList<MethodBasedRouter>();
        
        if (endpoint.getUriPattern() != null && endpoint.getUriPattern().length() > 0) {
            routers.add(getMethodRouter(endpoint.getUriPattern()));
        }
        
        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                routers.add(getMethodRouter(uriPattern));
            }
        }
        
        for (MethodBasedRouter router : routers) {
            if (endpoint.getRestletMethods() != null) {
                Method[] methods = endpoint.getRestletMethods();
                for (Method method : methods) {
                    router.removeRoute(method);
                }
            } else {
                router.removeRoute(endpoint.getRestletMethod());
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Detached restlet uriPattern: " + router.getUriPattern() 
                          + " method: " + endpoint.getRestletMethod());
            }
        }
    }    
    
    private MethodBasedRouter getMethodRouter(String uriPattern) {
        synchronized (routers) {
            MethodBasedRouter result = routers.get(uriPattern);
            if (result == null) {
                result = new MethodBasedRouter(uriPattern);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Added method based router: " + result);
                }
                routers.put(uriPattern, result);
            }
            return result;
        }    
    }
    
    private void addServerIfNecessary(RestletEndpoint endpoint) throws Exception {
        String key = buildKey(endpoint);
        Server server;
        synchronized (servers) {
            server = servers.get(key);
            if (server == null) {
                server = component.getServers().add(Protocol.valueOf(endpoint.getProtocol()), endpoint.getPort());
                servers.put(key, server);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Added server: " + key);
                }
                server.start();
            }
        }
    }
    
    private static String buildKey(RestletEndpoint endpoint) {
        return endpoint.getHost() + ":" + endpoint.getPort();
    }
    
    private void attachUriPatternToRestlet(String uriPattern, RestletEndpoint endpoint, Restlet target) {
        MethodBasedRouter router = getMethodRouter(uriPattern);
        
        Map<String, String> realm = endpoint.getRestletRealm();
        if (realm != null && realm.size() > 0) {
            Guard guard = new Guard(component.getContext().createChildContext(), 
                    ChallengeScheme.HTTP_BASIC, "Camel-Restlet Endpoint Realm");
            for (Map.Entry<String, String> entry : realm.entrySet()) {
                guard.getSecrets().put(entry.getKey(), entry.getValue().toCharArray());
            }
            guard.setNext(target);
            target = guard;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Target has been set to guard: " + guard);
            }
        }
        
        if (endpoint.getRestletMethods() != null) {
            Method[] methods = endpoint.getRestletMethods();
            for (Method method : methods) {
                router.addRoute(method, target);   
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attached restlet uriPattern: " + uriPattern + " method: " + method);
                }
            }
        } else {
            router.addRoute(endpoint.getRestletMethod(), target);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attached restlet uriPattern: " + uriPattern + " method: " + endpoint.getRestletMethod());
            }
        }
        
        if (!router.hasBeenAttached()) {
            component.getDefaultHost().attach(uriPattern, router);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attached methodRouter uriPattern: " + uriPattern);
            }
        }
    }

}

