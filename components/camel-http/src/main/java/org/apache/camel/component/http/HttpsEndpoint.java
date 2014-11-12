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
package org.apache.camel.component.http;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.spi.UriEndpoint;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

@UriEndpoint(scheme = "https", consumerClass = HttpConsumer.class)
public class HttpsEndpoint extends HttpEndpoint {

    public HttpsEndpoint() {
    }

    public HttpsEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        super(endPointURI, component, httpURI);
    }

    public HttpsEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpConnectionManager httpConnectionManager) throws URISyntaxException {
        super(endPointURI, component, httpURI, httpConnectionManager);
    }

    public HttpsEndpoint(String uri, HttpComponent component, HttpClientParams clientParams,
                         HttpConnectionManager connectionManager, HttpClientConfigurer configurer) throws URISyntaxException {
        super(uri, component, clientParams, connectionManager, configurer);
    }

    public HttpsEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientParams clientParams,
                         HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component, httpURI, clientParams, httpConnectionManager, clientConfigurer);
    }

    /**
     * The url of the HTTPS endpoint to call.
     */
    @Override
    public void setHttpUri(URI httpUri) {
        super.setHttpUri(httpUri);
    }
}
