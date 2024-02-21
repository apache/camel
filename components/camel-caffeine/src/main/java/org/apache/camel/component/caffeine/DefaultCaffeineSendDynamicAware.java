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
package org.apache.camel.component.caffeine;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

abstract class DefaultCaffeineSendDynamicAware extends SendDynamicAwareSupport {

    @Override
    public boolean isLenientProperties() {
        return false;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        Map<String, Object> properties = endpointProperties(exchange, uri);
        return new DynamicAwareEntry(uri, originalUri, properties, null);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        Object action = exchange.getMessage().getHeader(CaffeineConstants.ACTION);
        Object key = exchange.getMessage().getHeader(CaffeineConstants.KEY);
        Object keys = exchange.getMessage().getHeader(CaffeineConstants.KEYS);
        Object value = exchange.getMessage().getHeader(CaffeineConstants.VALUE);
        if (action == null && key == null && keys == null && value == null) {
            // remove keys
            Map<String, Object> copy = new LinkedHashMap<>(entry.getProperties());
            copy.remove("action");
            copy.remove("key");
            copy.remove("keys");
            copy.remove("value");
            // build static uri
            String u = entry.getUri();
            // remove query parameters
            if (u.contains("?")) {
                u = StringHelper.before(u, "?");
            }
            String query = URISupport.createQueryString(copy);
            if (!query.isEmpty()) {
                return u + "?" + query;
            } else {
                return u;
            }
        } else {
            // no optimisation possible
            return null;
        }
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // store as headers
        return ex -> {
            Object action = entry.getProperties().get("action");
            if (action != null) {
                ex.getMessage().setHeader(CaffeineConstants.ACTION, action);
            }
            Object key = entry.getProperties().get("key");
            if (key != null) {
                ex.getMessage().setHeader(CaffeineConstants.KEY, key);
            }
            Object keys = entry.getProperties().get("keys");
            if (keys != null) {
                ex.getMessage().setHeader(CaffeineConstants.KEYS, key);
            }
            Object value = entry.getProperties().get("value");
            if (value != null) {
                ex.getMessage().setHeader(CaffeineConstants.VALUE, value);
            }
        };
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // cleanup and remove the headers we used
        return ex -> {
            ex.getMessage().removeHeader(CaffeineConstants.ACTION);
            ex.getMessage().removeHeader(CaffeineConstants.KEY);
            ex.getMessage().removeHeader(CaffeineConstants.KEYS);
            ex.getMessage().removeHeader(CaffeineConstants.VALUE);
        };
    }
}
