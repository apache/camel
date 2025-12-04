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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Binding provider creates an object reference, usually to a Kubernetes resource. An object is identified by its fully
 * qualified reference with Kind, ApiVersion, name and optional namespace. In addition to that the reference is able to
 * specify resource properties. Subclasses may add logic for a very specific Kubernetes resource such as Kamelets or
 * Knative brokers.
 */
public class ObjectReferenceBindingProvider implements BindingProvider {

    private static final Pattern OBJECT_REFERENCE_URI_PATTERN = Pattern.compile(
            "^([a-z.]+/[alphbetv0-9]+):([A-Z][a-z]+):([a-z][a-z-]*/?[a-z][a-z-]*)\\??[^?]*", Pattern.DOTALL);

    private final String apiVersion;
    private final String kind;

    public ObjectReferenceBindingProvider() {
        this("", "");
    }

    protected ObjectReferenceBindingProvider(String apiVersion, String kind) {
        if (ObjectHelper.isNotEmpty(kind) && ObjectHelper.isEmpty(apiVersion)) {
            throw new IllegalArgumentException(
                    "Object reference provider with static kind '%s' requires apiVersion to be set.".formatted(kind));
        }

        this.apiVersion = apiVersion;
        this.kind = kind;
    }

    @Override
    public String getEndpoint(
            EndpointType type,
            String uriExpression,
            Map<String, Object> endpointProperties,
            TemplateProvider templateProvider)
            throws Exception {

        String apiVersionValue;
        String kindValue;
        String namespace;
        String objectName;
        if (ObjectHelper.isEmpty(kind)) {
            Matcher objectRef = OBJECT_REFERENCE_URI_PATTERN.matcher(uriExpression);
            if (objectRef.matches()) {
                apiVersionValue = objectRef.group(1);
                kindValue = objectRef.group(2);

                String namespacedName = objectRef.group(3);
                objectName = getObjectName(namespacedName);
                namespace = getNamespace(namespacedName);
            } else {
                throw new CamelException("Unsupported object reference: %s".formatted(uriExpression));
            }
        } else {
            apiVersionValue = apiVersion;
            kindValue = kind;
            objectName = getObjectName(uriExpression);
            namespace = getNamespace(uriExpression);
        }

        Map<String, Object> endpointUriProperties =
                getEndpointUriProperties(type, objectName, uriExpression, endpointProperties);

        InputStream is;
        if (type == EndpointType.STEP) {
            is = templateProvider.getStepTemplate("ref");
        } else {
            is = templateProvider.getEndpointTemplate("ref");
        }

        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        String namespaceContext = "";
        if (namespace != null) {
            namespaceContext = "      namespace: " + namespace + "\n";
        }

        context = context.replaceFirst("\\{\\{ \\.ApiVersion }}", apiVersionValue);
        context = context.replaceFirst("\\{\\{ \\.Kind }}", kindValue);
        context = context.replaceFirst("\\{\\{ \\.Name }}", objectName);
        context = context.replaceFirst("\\{\\{ \\.Namespace }}\n", namespaceContext);
        context = context.replaceFirst(
                "\\{\\{ \\.EndpointProperties }}\n", templateProvider.asEndpointProperties(endpointUriProperties));

        return context;
    }

    protected String getObjectName(String uriExpression) {
        String namespacedName = uriExpression;
        if (uriExpression.contains("?")) {
            namespacedName = StringHelper.before(uriExpression, "?");
        }

        if (namespacedName.contains("/")) {
            return namespacedName.split("/", 2)[1];
        }

        return namespacedName;
    }

    protected String getNamespace(String uriExpression) {
        String namespacedName = uriExpression;
        if (uriExpression.contains("?")) {
            namespacedName = StringHelper.before(uriExpression, "?");
        }

        if (namespacedName.contains("/")) {
            return namespacedName.split("/", 2)[0];
        }

        return null;
    }

    protected Map<String, Object> getEndpointUriProperties(
            EndpointType type, String objectName, String uriExpression, Map<String, Object> endpointProperties)
            throws Exception {
        Map<String, Object> endpointUriProperties = new HashMap<>(endpointProperties);
        if (uriExpression.contains("?")) {
            String query = StringHelper.after(uriExpression, "?");
            if (query != null) {
                endpointUriProperties = URISupport.parseQuery(query, true);
            }
        }

        return endpointUriProperties;
    }

    @Override
    public boolean canHandle(String uriExpression) {
        if (ObjectHelper.isNotEmpty(kind)) {
            return uriExpression.startsWith(kind.toLowerCase(Locale.US) + ":");
        }

        return OBJECT_REFERENCE_URI_PATTERN.matcher(uriExpression).matches();
    }
}
