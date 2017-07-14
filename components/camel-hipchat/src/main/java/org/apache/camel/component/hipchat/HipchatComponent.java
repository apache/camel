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
package org.apache.camel.component.hipchat;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link HipchatEndpoint}. Hipchat is an Atlassian software for team chat.
 *
 * The hipchat component uses the OAuth2 Hipchat API to produce/consume messages. For more details about Hipchat API
 * @see <a href="https://www.hipchat.com/docs/apiv2/auth">Hipchat API</a>. You can get the Oauth2 auth token
 * at @see <a href="https://www.hipchat.com/account/api">Hipchat Auth Token</a>. The messages produced and consumed
 * would be from/to owner of the provided auth token.
 */
public class HipchatComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HipchatComponent.class);

    public HipchatComponent() {
        super(HipchatEndpoint.class);
    }

    public HipchatComponent(CamelContext context) {
        super(context, HipchatEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HipchatEndpoint endpoint = getHipchatEndpoint(uri);
        setProperties(endpoint.getConfiguration(), parameters);
        if (endpoint.getConfiguration().getAuthToken() == null) {
            throw new HipchatException("OAuth 2 auth token must be specified");
        }
        parseUri(remaining, endpoint);
        LOG.debug("Using Hipchat API URL: {}", endpoint.getConfiguration().hipChatUrl());
        return endpoint;
    }

    private void parseUri(String remaining, HipchatEndpoint endpoint) throws Exception {
        String uri = URISupport.normalizeUri(remaining);

        URI hipChatUri = new URI(uri);
        if (hipChatUri.getHost() != null) {
            endpoint.getConfiguration().setHost(hipChatUri.getHost());
            if (hipChatUri.getPort() != -1) {
                endpoint.getConfiguration().setPort(hipChatUri.getPort());
            }
            endpoint.getConfiguration().setProtocol(hipChatUri.getScheme());
        }
    }

    protected HipchatEndpoint getHipchatEndpoint(String uri) {
        return new HipchatEndpoint(uri, this);
    }
}
