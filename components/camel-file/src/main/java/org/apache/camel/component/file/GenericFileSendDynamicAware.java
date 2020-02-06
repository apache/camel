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
package org.apache.camel.component.file;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.util.URISupport;

public abstract class GenericFileSendDynamicAware implements SendDynamicAware {

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
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        Map<String, String> properties = catalog.endpointProperties(uri);
        Map<String, String> lenient = catalog.endpointLenientProperties(uri);
        return new DynamicAwareEntry(uri, originalUri, properties, lenient);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        boolean fileName = entry.getProperties().containsKey("fileName");
        boolean tempFileName = entry.getProperties().containsKey("tempFileName");
        boolean idempotentKey = entry.getProperties().containsKey("idempotentKey");
        boolean move = entry.getProperties().containsKey("move");
        boolean moveFailed = entry.getProperties().containsKey("moveFailed");
        boolean preMove = entry.getProperties().containsKey("preMove");
        boolean moveExisting = entry.getProperties().containsKey("moveExisting");
        // if any of the above are in use, then they should not be pre evaluated
        // and we need to rebuild a new uri with them as-is
        if (fileName || tempFileName || idempotentKey || move || moveFailed || preMove || moveExisting) {
            Map<String, String> params = new LinkedHashMap<>(entry.getProperties());

            Map<String, Object> originalParams = URISupport.parseQuery(entry.getOriginalUri());
            if (fileName) {
                Object val = originalParams.get("fileName");
                if (val != null) {
                    params.put("fileName", val.toString());
                }
            }
            if (tempFileName) {
                Object val = originalParams.get("tempFileName");
                if (val != null) {
                    params.put("tempFileName", val.toString());
                }
            }
            if (idempotentKey) {
                Object val = originalParams.get("idempotentKey");
                if (val != null) {
                    params.put("idempotentKey", val.toString());
                }
            }
            if (move) {
                Object val = originalParams.get("move");
                if (val != null) {
                    params.put("move", val.toString());
                }
            }
            if (moveFailed) {
                Object val = originalParams.get("moveFailed");
                if (val != null) {
                    params.put("moveFailed", val.toString());
                }
            }
            if (preMove) {
                Object val = originalParams.get("preMove");
                if (val != null) {
                    params.put("preMove", val.toString());
                }
            }
            if (moveExisting) {
                Object val = originalParams.get("moveExisting");
                if (val != null) {
                    params.put("moveExisting", val.toString());
                }
            }

            RuntimeCamelCatalog catalog = exchange.getContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
            return catalog.asEndpointUri(scheme, params, false);
        } else {
            return entry.getUri();
        }
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        return null;
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        return null;
    }
}
