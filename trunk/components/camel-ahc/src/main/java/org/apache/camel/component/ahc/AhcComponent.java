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
package org.apache.camel.component.ahc;

import java.net.URI;
import java.util.Map;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.URISupport;

/**
 *  Defines the <a href="http://camel.apache.org/ahc.html">Async HTTP Client Component</a>
 */
public class AhcComponent extends HeaderFilterStrategyComponent {

    private AsyncHttpClient client;
    private AsyncHttpClientConfig clientConfig;
    private AhcBinding binding;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = remaining;

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(parameters));

        AhcEndpoint endpoint = new AhcEndpoint(uri, this, httpUri);
        setEndpointHeaderFilterStrategy(endpoint);
        endpoint.setClient(getClient());
        endpoint.setClientConfig(getClientConfig());
        endpoint.setBinding(getBinding());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AhcBinding getBinding() {
        if (binding == null) {
            binding = new DefaultAhcBinding();
        }
        return binding;
    }

    public void setBinding(AhcBinding binding) {
        this.binding = binding;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }
}
