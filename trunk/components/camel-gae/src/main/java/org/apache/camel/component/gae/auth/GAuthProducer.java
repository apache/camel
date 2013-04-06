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
package org.apache.camel.component.gae.auth;

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultProducer;

import static org.apache.camel.component.gae.auth.GAuthEndpoint.Name.AUTHORIZE;

public class GAuthProducer extends DefaultProducer {

    public GAuthProducer(GAuthEndpoint endpoint) {
        super(endpoint);
    }
    
    @Override
    public GAuthEndpoint getEndpoint() {
        return (GAuthEndpoint)super.getEndpoint();
    }
    
    public OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> getAuthorizeBinding() {
        return getEndpoint().getAuthorizeBinding();
    }

    public OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> getUpgradeBinding() {
        return getEndpoint().getUpgradeBinding();
    }

    /**
     * Depending on the {@link GAuthEndpoint.Name}, this method either fetches
     * an unauthorized request token and creates a redirect response, or
     * upgrades an authorized request token to an access token.
     */
    public void process(Exchange exchange) throws Exception {
        if (getEndpoint().getName() == AUTHORIZE) {
            GoogleOAuthParameters params = getAuthorizeBinding().writeRequest(getEndpoint(), exchange, null);
            getEndpoint().getService().getUnauthorizedRequestToken(params);
            getAuthorizeBinding().readResponse(getEndpoint(), exchange, params);
        } else {
            GoogleOAuthParameters params = getUpgradeBinding().writeRequest(getEndpoint(), exchange, null);
            getEndpoint().getService().getAccessToken(params);
            getUpgradeBinding().readResponse(getEndpoint(), exchange, params);
        }
    }

}
