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
package org.apache.camel.component.http.helper;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;

/**
 * Helper methods for HTTP producers.
 *
 * @version $Revision$
 */
public final class HttpProducerHelper {

    private HttpProducerHelper() {
    }

    /**
     * Creates the URL to invoke.
     *
     * @param exchange the exchange
     * @param endpoint the endpoint
     * @return the URL to invoke
     */
    public static String createURL(Exchange exchange, HttpEndpoint endpoint) {
        String uri = null;
        if (!(endpoint.isBridgeEndpoint())) {
            uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        }
        if (uri == null) {
            uri = endpoint.getHttpUri().toString();
        }

        // append HTTP_PATH to HTTP_URI if it is provided in the header
        String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null) {
            if (path.startsWith("/")) {
                URI baseURI;
                String baseURIString = exchange.getIn().getHeader(Exchange.HTTP_BASE_URI, String.class);
                try {
                    if (baseURIString == null) {
                        if (exchange.getFromEndpoint() != null) {
                            baseURIString = exchange.getFromEndpoint().getEndpointUri();
                        } else {
                            // will set a default one for it
                            baseURIString = "/";
                        }
                    }
                    baseURI = new URI(baseURIString);
                    String basePath = baseURI.getPath();
                    if (path.startsWith(basePath)) {
                        path = path.substring(basePath.length());
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }
                    } else {
                        throw new RuntimeCamelException("Can't anylze the Exchange.HTTP_PATH header, due to: can't find the right HTTP_BASE_URI");
                    }
                } catch (Throwable t) {
                    throw new RuntimeCamelException("Can't anylze the Exchange.HTTP_PATH header, due to: "
                                                    + t.getMessage(), t);
                }

            }
            if (path.length() > 0) {
                // make sure that there is exactly one "/" between HTTP_URI and
                // HTTP_PATH
                if (!uri.endsWith("/")) {
                    uri = uri + "/";
                }
                uri = uri.concat(path);
            }
        }

        return uri;
    }

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     *
     * @param exchange  the exchange
     * @return the created method
     */
    public static HttpMethods createMethod(Exchange exchange, HttpEndpoint endpoint, boolean hasPayload) {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = endpoint.getHttpUri().getQuery();
        }

        // compute what method to use either GET or POST
        HttpMethods answer;
        HttpMethods m = exchange.getIn().getHeader(Exchange.HTTP_METHOD, HttpMethods.class);
        if (m != null) {
            // always use what end-user provides in a header
            answer = m;
        } else if (queryString != null) {
            // if a query string is provided then use GET
            answer = HttpMethods.GET;
        } else {
            // fallback to POST if we have payload, otherwise GET
            answer = hasPayload ? HttpMethods.POST : HttpMethods.GET;
        }

        return answer;
    }

}
