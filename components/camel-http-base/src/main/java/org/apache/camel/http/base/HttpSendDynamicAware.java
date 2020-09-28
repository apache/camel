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
package org.apache.camel.http.base;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * HTTP based {@link org.apache.camel.spi.SendDynamicAware} which allows to optimise HTTP components with the toD
 * (dynamic to) DSL in Camel. This implementation optimises by allowing to provide dynamic parameters via
 * {@link Exchange#HTTP_PATH} and {@link Exchange#HTTP_QUERY} headers instead of the endpoint uri. That allows to use a
 * static endpoint and its producer to service dynamic requests.
 */
public class HttpSendDynamicAware extends SendDynamicAwareSupport {

    private final Processor postProcessor = new HttpSendDynamicPostProcessor();

    @Override
    public boolean isOnlyDynamicQueryParameters() {
        // we compute our own host:port/path so its okay so say true here
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        Map<String, Object> properties = endpointProperties(exchange, uri);
        Map<String, Object> lenient = endpointLenientProperties(exchange, uri);
        return new DynamicAwareEntry(uri, originalUri, properties, lenient);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        String[] hostAndPath = parseUri(entry);
        String host = hostAndPath[0];
        String path = hostAndPath[1];
        if (path != null || !entry.getLenientProperties().isEmpty()) {
            // the context path can be dynamic or any lenient properties
            // and therefore build a new static uri without path or lenient options
            Map<String, Object> params = entry.getProperties();
            for (String k : entry.getLenientProperties().keySet()) {
                params.remove(k);
            }
            if (path != null) {
                params.remove("httpUri");
                params.remove("httpURI");
                if ("netty-http".equals(getScheme())) {
                    // the netty-http stores host,port etc in other fields than httpURI so we can just remove the path parameter
                    params.remove("path");
                }
            }

            // build static url with the known parameters
            String url = getScheme() + ":" + host;
            if (!params.isEmpty()) {
                url += "?" + URISupport.createQueryString(params, false);
            }
            return url;
        } else {
            // no need for optimisation
            return null;
        }
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        String[] hostAndPath = parseUri(entry);
        String path = hostAndPath[1];
        String query = null;
        if (!entry.getLenientProperties().isEmpty()) {
            // all lenient properties can be dynamic and provided in the HTTP_QUERY header
            query = URISupport.createQueryString(new LinkedHashMap<>(entry.getLenientProperties()));
        }

        if (query == null && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Exchange.HTTP_QUERY))) {
            query = (String) exchange.getIn().getHeader(Exchange.HTTP_QUERY);
        }

        if (path != null || query != null) {
            return new HttpSendDynamicPreProcessor(path, query);
        } else {
            // no optimisation
            return null;
        }
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        return postProcessor;
    }

    public String[] parseUri(DynamicAwareEntry entry) {
        String u = entry.getUri();

        // remove scheme prefix (unless its camel-http or camel-http)
        boolean httpComponent = "http".equals(getScheme()) || "https".equals(getScheme());
        if (!httpComponent) {
            String prefix = getScheme() + "://";
            String prefix2 = getScheme() + ":";
            if (u.startsWith(prefix)) {
                u = u.substring(prefix.length());
            } else if (u.startsWith(prefix2)) {
                u = u.substring(prefix2.length());
            }
        }

        // remove query parameters
        if (u.indexOf('?') > 0) {
            u = StringHelper.before(u, "?");
        }

        // favour using java.net.URI for parsing into host and context-path
        try {
            URI parse = new URI(u);
            String host = parse.getHost();
            String path = parse.getPath();
            // if the path is just a trailing slash then skip it (eg it must be longer than just the slash itself)
            if (path != null && path.length() > 1) {
                int port = parse.getPort();
                if (port > 0 && port != 80 && port != 443) {
                    host += ":" + port;
                }
                // remove double slash for path
                while (path.startsWith("//")) {
                    path = path.substring(1);
                }
                if (!httpComponent) {
                    // include scheme for components that are not camel-http
                    String scheme = parse.getScheme();
                    if (scheme != null) {
                        host = scheme + "://" + host;
                    }
                }
                return new String[] { host, path };
            }
        } catch (URISyntaxException e) {
            // ignore
            return new String[] { u, null };
        }

        // no context path
        return new String[] { u, null };
    }

}
