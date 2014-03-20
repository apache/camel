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
package org.apache.camel.component.ws;

import java.net.URI;
import java.util.Map;

import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Defines the <a href="http://camel.apache.org/ahc.html">Async HTTP Client Component</a>
 */
public class WsComponent extends DefaultComponent {
    
    private static final Logger LOG = LoggerFactory.getLogger(WsComponent.class);
    
    private static final String CLIENT_CONFIG_PREFIX = "clientConfig.";
    private static final String CLIENT_REALM_CONFIG_PREFIX = "clientConfig.realm.";

    private AsyncHttpClientConfig clientConfig;
    private SSLContextParameters sslContextParameters;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = uri;

        // Do not set the URI because we still have all of the Camel internal
        // parameters in the URI at this point.
        WsEndpoint endpoint = new WsEndpoint(uri, this);
        endpoint.setClientConfig(getClientConfig());
        endpoint.setSslContextParameters(getSslContextParameters());
        
        setProperties(endpoint, parameters);

        if (IntrospectionSupport.hasProperties(parameters, CLIENT_CONFIG_PREFIX)) {
            AsyncHttpClientConfig.Builder builder = endpoint.getClientConfig() == null 
                    ? new AsyncHttpClientConfig.Builder() : WsComponent.cloneConfig(endpoint.getClientConfig());
            
            if (endpoint.getClientConfig() != null) {
                LOG.warn("The user explicitly set an AsyncHttpClientConfig instance on the component or "
                         + "endpoint, but this endpoint URI contains client configuration parameters.  "
                         + "Are you sure that this is what was intended?  The URI parameters will be applied"
                         + " to a clone of the supplied AsyncHttpClientConfig in order to prevent unintended modification"
                         + " of the explicitly configured AsyncHttpClientConfig.  That is, the URI parameters override the"
                         + " settings on the explicitly configured AsyncHttpClientConfig for this endpoint.");
            }

            // special for realm builder
            Realm.RealmBuilder realmBuilder = null;
            if (IntrospectionSupport.hasProperties(parameters, CLIENT_REALM_CONFIG_PREFIX)) {
                realmBuilder = new Realm.RealmBuilder();

                // set and validate additional parameters on client config
                Map<String, Object> realmParams = IntrospectionSupport.extractProperties(parameters, CLIENT_REALM_CONFIG_PREFIX);
                setProperties(realmBuilder, realmParams);
                validateParameters(uri, realmParams, null);
            }

            // set and validate additional parameters on client config
            Map<String, Object> clientParams = IntrospectionSupport.extractProperties(parameters, CLIENT_CONFIG_PREFIX);
            setProperties(builder, clientParams);
            validateParameters(uri, clientParams, null);

            if (realmBuilder != null) {
                builder.setRealm(realmBuilder.build());
            }
            endpoint.setClientConfig(builder.build());
        }

        SSLContextParameters sslparams = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        
        // prefer to use endpoint configured over component configured
        if (sslparams == null) {
            // fallback to component configured
            sslparams = getSslContextParameters();
        }
        if (sslparams != null) {
            endpoint.setSslContextParameters(sslparams);
        }

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        addressUri = UnsafeUriCharactersEncoder.encode(addressUri);
        URI wsuri = URISupport.createRemainingURI(new URI(addressUri), parameters);
        endpoint.setWsUri(wsuri);
        
        return endpoint;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
    
    /**
     * Creates a new client configuration builder using {@code clientConfig} as a template for
     * the builder.
     *
     * @param clientConfig the instance to serve as a template for the builder
     *
     * @return a builder configured with the same options as the supplied config
     */
    static AsyncHttpClientConfig.Builder cloneConfig(AsyncHttpClientConfig clientConfig) {

        AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder(clientConfig);

        return builder;
    }
}
