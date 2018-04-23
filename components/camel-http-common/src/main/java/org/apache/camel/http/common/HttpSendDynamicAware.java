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
package org.apache.camel.http.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * HTTP based {@link SendDynamicAware} which allows to optimise HTTP components
 * with the toD (dynamic to) DSL in Camel. This implementation optimises by allowing
 * to provide dynamic parameters via {@link Exchange#HTTP_PATH} and {@link Exchange#HTTP_QUERY} headers
 * instead of the endpoint uri. That allows to use a static endpoint and its producer to service
 * dynamic requests.
 */
public class HttpSendDynamicAware implements SendDynamicAware {

    private String scheme;

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().getRuntimeCamelCatalog();
        Map<String, String> properties = catalog.endpointProperties(uri);
        Map<String, String> lenient = catalog.endpointLenientProperties(uri);
        return new DynamicAwareEntry(uri, properties, lenient);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        String[] hostAndPath = parseUri(entry.getOriginalUri());
        String host = hostAndPath[0];
        String path = hostAndPath[1];
        if (path != null || !entry.getLenientProperties().isEmpty()) {
            // the context path can be dynamic or any lenient properties
            // and therefore build a new static uri without path or lenient options
            Map<String, String> params = new LinkedHashMap<>(entry.getProperties());
            for (String k : entry.getLenientProperties().keySet()) {
                params.remove(k);
            }
            if (path != null) {
                // httpUri contains the host and path, so replace it with just the host as the context-path is dynamic
                params.put("httpUri", host);
            }
            RuntimeCamelCatalog catalog = exchange.getContext().getRuntimeCamelCatalog();
            return catalog.asEndpointUri(scheme, params, false);
        } else {
            // no need for optimisation
            return null;
        }
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        Processor pathProcessor = null;
        Processor lenientProcessor = null;

        String[] hostAndPath = parseUri(entry.getOriginalUri());
        String path = hostAndPath[1];

        if (path != null) {
            pathProcessor = new SetHeaderProcessor(ExpressionBuilder.constantExpression(Exchange.HTTP_PATH), ExpressionBuilder.constantExpression(path));
        }

        if (!entry.getLenientProperties().isEmpty()) {
            // all lenient properties can be dynamic and provided in the HTTP_QUERY header
            String query = URISupport.createQueryString(new LinkedHashMap<>(entry.getLenientProperties()));
            lenientProcessor = new SetHeaderProcessor(ExpressionBuilder.constantExpression(Exchange.HTTP_QUERY), ExpressionBuilder.constantExpression(query));
        }

        if (pathProcessor != null || lenientProcessor != null) {
            List<Processor> list = new ArrayList<>(2);
            if (pathProcessor != null) {
                list.add(pathProcessor);
            }
            if (lenientProcessor != null) {
                list.add(lenientProcessor);
            }
            if (list.size() == 2) {
                return new Pipeline(exchange.getContext(), list);
            } else {
                return list.get(0);
            }
        }

        return null;
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // no need to cleanup
        return null;
    }

    private String[] parseUri(String uri) {
        String u = uri;

        // remove scheme prefix
        String prefix = scheme + "://";
        if (uri.startsWith(prefix)) {
            u = uri.substring(prefix.length());
        }
        // remove query parameters
        if (u.indexOf('?') > 0) {
            u = StringHelper.before(u, "?");

        }
        // split into host and context-path
        int dash = u.indexOf('/');
        if (dash > 0) {
            String host = u.substring(0, dash);
            String path = u.substring(dash);
            // if the path is just a trailing slash then skip it (eg it must be longer than just the slash itself)
            if (path.length() > 1) {
                return new String[] {host, path};
            }
        }
        // no context path
        return new String[]{u, null};
    }
    
}
