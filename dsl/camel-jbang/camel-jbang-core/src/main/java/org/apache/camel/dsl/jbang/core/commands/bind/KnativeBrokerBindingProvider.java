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

import java.util.Map;

import org.apache.camel.util.StringHelper;

/**
 * Binding to a Knative broker resource.
 */
public class KnativeBrokerBindingProvider extends ObjectReferenceBindingProvider {

    private static final String prefix = "knative:broker:";

    public KnativeBrokerBindingProvider() {
        super("eventing.knative.dev/v1", "Broker");
    }

    @Override
    public String getEndpoint(
            EndpointType type,
            String uriExpression,
            Map<String, Object> endpointProperties,
            TemplateProvider templateProvider)
            throws Exception {
        if (uriExpression.startsWith(prefix)) {
            return super.getEndpoint(
                    type, StringHelper.after(uriExpression, prefix), endpointProperties, templateProvider);
        }

        return super.getEndpoint(type, uriExpression, endpointProperties, templateProvider);
    }

    @Override
    protected Map<String, Object> getEndpointUriProperties(
            EndpointType type, String objectName, String uriExpression, Map<String, Object> endpointProperties)
            throws Exception {
        Map<String, Object> props = super.getEndpointUriProperties(type, objectName, uriExpression, endpointProperties);

        if (type == EndpointType.SOURCE && !props.containsKey("type")) {
            // When acting as a source the type property is added by default in order to filter the event stream.
            props.put("type", "org.apache.camel.event");
        }

        return props;
    }

    @Override
    public boolean canHandle(String uriExpression) {
        return uriExpression.startsWith(prefix);
    }
}
