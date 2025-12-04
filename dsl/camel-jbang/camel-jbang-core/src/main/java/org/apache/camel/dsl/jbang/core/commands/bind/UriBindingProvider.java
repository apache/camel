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

package org.apache.camel.dsl.jbang.core.commands.bind;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public class UriBindingProvider implements BindingProvider {

    private static final Pattern CAMEL_ENDPOINT_URI_PATTERN = Pattern.compile("^[a-z0-9+][a-zA-Z0-9-+]*:.*$");

    @Override
    public String getEndpoint(
            EndpointType type,
            String uriExpression,
            Map<String, Object> endpointProperties,
            TemplateProvider templateProvider)
            throws Exception {
        String endpointUri = uriExpression;
        Map<String, Object> endpointUriProperties = new HashMap<>();
        if (uriExpression.contains("?")) {
            endpointUri = StringHelper.before(uriExpression, "?");
            String query = StringHelper.after(uriExpression, "?");
            if (query != null) {
                endpointUriProperties = URISupport.parseQuery(query, true);
            }
        }

        endpointProperties.putAll(endpointUriProperties);

        InputStream is;
        if (type == EndpointType.STEP) {
            is = templateProvider.getStepTemplate("uri");
        } else {
            is = templateProvider.getEndpointTemplate("uri");
        }

        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.URI }}", endpointUri);
        context = context.replaceFirst(
                "\\{\\{ \\.EndpointProperties }}\n", templateProvider.asEndpointProperties(endpointProperties));

        return context;
    }

    @Override
    public boolean canHandle(String uriExpression) {
        return CAMEL_ENDPOINT_URI_PATTERN.matcher(uriExpression).matches();
    }
}
