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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.apache.camel.util.URISupport;

public abstract class GenericFileSendDynamicAware extends SendDynamicAwareSupport {

    public static final String PROP_FILE_NAME = "fileName";
    public static final String PROP_TEMP_FILE_NAME = "tempFileName";
    public static final String PROP_IDEMPOTENT_KEY = "idempotentKey";
    public static final String PROP_MOVE = "move";
    public static final String PROP_MOVE_FAILED = "moveFailed";
    public static final String PROP_PRE_MOVE = "preMove";
    public static final String PROP_MOVE_EXISTING = "moveExisting";

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
        String uri = entry.getUri();
        // windows path problems such as C:\temp was by simple language evaluated \t as a tab character
        // which should then be reversed
        uri = uri.replace("\t", "\\\\t");

        boolean fileName = entry.getProperties().containsKey(PROP_FILE_NAME);
        boolean tempFileName = entry.getProperties().containsKey(PROP_TEMP_FILE_NAME);
        boolean idempotentKey = entry.getProperties().containsKey(PROP_IDEMPOTENT_KEY);
        boolean move = entry.getProperties().containsKey(PROP_MOVE);
        boolean moveFailed = entry.getProperties().containsKey(PROP_MOVE_FAILED);
        boolean preMove = entry.getProperties().containsKey(PROP_PRE_MOVE);
        boolean moveExisting = entry.getProperties().containsKey(PROP_MOVE_EXISTING);
        // if any of the above are in use, then they should not be pre evaluated
        // and we need to rebuild a new uri with them as-is
        if (fileName || tempFileName || idempotentKey || move || moveFailed || preMove || moveExisting) {
            Map<String, Object> params = entry.getProperties();

            Map<String, Object> originalParams = URISupport.parseQuery(URISupport.extractQuery(entry.getOriginalUri()));
            if (fileName) {
                Object val = originalParams.get(PROP_FILE_NAME);
                if (val != null) {
                    params.put(PROP_FILE_NAME, val.toString());
                }
            }
            if (tempFileName) {
                Object val = originalParams.get(PROP_TEMP_FILE_NAME);
                if (val != null) {
                    params.put(PROP_TEMP_FILE_NAME, val.toString());
                }
            }
            if (idempotentKey) {
                Object val = originalParams.get(PROP_IDEMPOTENT_KEY);
                if (val != null) {
                    params.put(PROP_IDEMPOTENT_KEY, val.toString());
                }
            }
            if (move) {
                Object val = originalParams.get(PROP_MOVE);
                if (val != null) {
                    params.put(PROP_MOVE, val.toString());
                }
            }
            if (moveFailed) {
                Object val = originalParams.get(PROP_MOVE_FAILED);
                if (val != null) {
                    params.put(PROP_MOVE_FAILED, val.toString());
                }
            }
            if (preMove) {
                Object val = originalParams.get(PROP_PRE_MOVE);
                if (val != null) {
                    params.put(PROP_PRE_MOVE, val.toString());
                }
            }
            if (moveExisting) {
                Object val = originalParams.get(PROP_MOVE_EXISTING);
                if (val != null) {
                    params.put(PROP_MOVE_EXISTING, val.toString());
                }
            }

            return asEndpointUri(exchange, uri, params);
        } else {
            return uri;
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
