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
package org.apache.camel.component.xmpp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.jivesoftware.smack.ReconnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("xmpp")
public class XmppComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(XmppComponent.class);

    // keep a cache of endpoints so they can be properly cleaned up
    private final Map<String, XmppEndpoint> endpointCache = new HashMap<>();

    public XmppComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String cacheKey = extractCacheKeyFromUri(uri);
        if (endpointCache.containsKey(cacheKey)) {
            LOG.debug("Using cached endpoint for URI {}", URISupport.sanitizeUri(uri));
            return endpointCache.get(cacheKey);
        }

        LOG.debug("Creating new endpoint for URI {}", URISupport.sanitizeUri(uri));
        XmppEndpoint endpoint = new XmppEndpoint(uri, this);

        URI u = new URI(uri);
        endpoint.setHost(u.getHost());
        endpoint.setPort(u.getPort());
        if (u.getUserInfo() != null) {
            String[] parts = u.getUserInfo().split(":");
            if (parts.length == 2) {
                endpoint.setUser(parts[0]);
                endpoint.setPassword(parts[1]);
            } else {
                endpoint.setUser(u.getUserInfo());
            }
        }
        String remainingPath = u.getPath();
        if (remainingPath != null) {
            if (remainingPath.startsWith("/")) {
                remainingPath = remainingPath.substring(1);
            }

            // assume its a participant
            if (remainingPath.length() > 0) {
                endpoint.setParticipant(remainingPath);
            }
        }

        endpointCache.put(cacheKey, endpoint);

        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ReconnectionManager.setEnabledPerDefault(true);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(endpointCache.values());
        endpointCache.clear();

        super.doStop();
    }

    private String extractCacheKeyFromUri(String uri) throws URISyntaxException {
        URI u = new URI(uri);
        return u.getScheme() + "://" + u.getHost() + u.getPort() + u.getQuery();
    }
}
