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
import org.apache.camel.support.component.PollDynamicAwareSupport;
import org.apache.camel.util.URISupport;

public abstract class GenericFilePollDynamicAware extends PollDynamicAwareSupport {

    public static final String PROP_FILE_NAME = "fileName";

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
        // if any of the above are in use, then they should not be pre evaluated
        // and we need to rebuild a new uri with them as-is
        if (fileName) {
            Map<String, Object> params = entry.getProperties();
            Map<String, Object> originalParams = URISupport.parseQuery(URISupport.extractQuery(entry.getOriginalUri()));
            compute(originalParams, PROP_FILE_NAME, params);
            return asEndpointUri(exchange, uri, params);
        } else {
            return uri;
        }
    }

    private static void compute(Map<String, Object> originalParams, String propFileName, Map<String, Object> params) {
        Object val = originalParams.get(propFileName);
        if (val != null) {
            params.put(propFileName, val.toString());
        }
    }

}
