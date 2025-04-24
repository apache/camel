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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

public class DownloadEndpointStrategy implements EndpointStrategy {

    private final CamelContext camelContext;

    public DownloadEndpointStrategy(CamelContext camelContext, boolean silent) {
        this.camelContext = camelContext;
    }

    @Override
    public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
        String scheme = StringHelper.before(uri, ":");

        if ("language".equals(scheme)) {
            String name = extractName(uri);
            if (name != null) {
                camelContext.resolveLanguage(name);
            }
        } else if ("dataformat".equals(scheme)) {
            String name = extractName(uri);
            if (name != null) {
                camelContext.resolveDataFormat(name);
            }
        }
        return endpoint;
    }

    private static String extractName(String uri) {
        uri = StringHelper.before(uri, "?", uri);
        int count = StringHelper.countChar(uri, ':');
        String name;
        if (count > 1) {
            name = StringHelper.between(uri, ":", ":");
        } else {
            name = StringHelper.after(uri, ":");
        }
        name = FileUtil.stripLeadingSeparator(name);
        return name;
    }

}
