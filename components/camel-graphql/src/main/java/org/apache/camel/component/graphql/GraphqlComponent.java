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
package org.apache.camel.component.graphql;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.URISupport;
import org.apache.hc.client5.http.classic.HttpClient;

@Component("graphql")
public class GraphqlComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private HttpClient httpClient;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GraphqlEndpoint endpoint = new GraphqlEndpoint(uri, this);
        endpoint.setHttpClient(httpClient);
        endpoint.setHttpUri(new URI(remaining));
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters)
            throws Exception {
        GraphqlEndpoint graphqlEndpoint = (GraphqlEndpoint) endpoint;
        if (!parameters.isEmpty()) {
            URI httpUri = URISupport.createRemainingURI(graphqlEndpoint.getHttpUri(), parameters);
            graphqlEndpoint.setHttpUri(httpUri);
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * To use a custom pre-existing Http Client. Beware that when using this, then other configurations such as proxy,
     * access token, is not applied and all this must be pre-configured on the Http Client.
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

}
