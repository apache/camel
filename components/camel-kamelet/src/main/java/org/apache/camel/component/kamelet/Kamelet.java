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
package org.apache.camel.component.kamelet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

public final class Kamelet {
    public static final String PROPERTIES_PREFIX = "camel.kamelet.";
    public static final String ENV_VAR_PREFIX = "CAMEL_KAMELET_";
    public static final String SCHEME = "kamelet";
    public static final String SOURCE_ID = "source";
    public static final String SINK_ID = "sink";
    public static final String PARAM_ROUTE_ID = "routeId";
    public static final String PARAM_TEMPLATE_ID = "templateId";
    public static final String PARAM_LOCATION = "location";
    public static final String PARAM_UUID = "uuid";
    public static final String DEFAULT_LOCATION = "classpath:kamelets";
    public static final String PARENT_ROUTE_ID = "parentRouteId";
    public static final String NO_ERROR_HANDLER = "noErrorHandler";

    // use a running counter as uuid
    private static final UuidGenerator UUID = new SimpleUuidGenerator();

    private Kamelet() {
    }

    public static Predicate<String> startsWith(String prefix) {
        return item -> item.startsWith(prefix);
    }

    public static String extractTemplateId(CamelContext context, String remaining, Map<String, Object> parameters) {
        Object param = parameters.get(PARAM_TEMPLATE_ID);
        if (param != null) {
            return CamelContextHelper.mandatoryConvertTo(context, String.class, param);
        }

        if (SOURCE_ID.equals(remaining) || SINK_ID.equals(remaining)) {
            return context.resolvePropertyPlaceholders("{{" + PARAM_TEMPLATE_ID + "}}");
        }

        String answer = null;
        if (remaining != null) {
            answer = StringHelper.before(remaining, "/");
        }
        if (answer == null) {
            answer = remaining;
        }

        return answer;
    }

    public static String extractUuid() {
        return UUID.generateUuid();
    }

    public static String extractRouteId(CamelContext context, String remaining, Map<String, Object> parameters, String uuid) {
        Object param = parameters.get(PARAM_ROUTE_ID);
        if (param != null) {
            return CamelContextHelper.mandatoryConvertTo(context, String.class, param);
        }

        if (SOURCE_ID.equals(remaining) || SINK_ID.equals(remaining)) {
            return context.resolvePropertyPlaceholders("{{" + PARAM_ROUTE_ID + "}}");
        }

        String answer = null;
        if (remaining != null) {
            answer = StringHelper.after(remaining, "/");
        }
        if (answer == null) {
            answer = extractTemplateId(context, remaining, parameters) + "-" + uuid;
        }

        return answer;
    }

    public static String extractLocation(CamelContext context, Map<String, Object> parameters) {
        Object param = parameters.get(PARAM_LOCATION);
        if (param != null) {
            return CamelContextHelper.mandatoryConvertTo(context, String.class, param);
        }
        return null;
    }

    public static void extractKameletProperties(CamelContext context, Map<String, Object> properties, String... elements) {
        PropertiesComponent pc = context.getPropertiesComponent();
        StringBuilder prefixBuffer = new StringBuilder(Kamelet.PROPERTIES_PREFIX);

        for (String element : elements) {
            if (element == null) {
                continue;
            }
            prefixBuffer.append(element).append('.');

            Properties prefixed = pc.loadProperties(Kamelet.startsWith(prefixBuffer.toString()));
            for (String name : prefixed.stringPropertyNames()) {
                properties.put(name.substring(prefixBuffer.toString().length()), prefixed.getProperty(name));
            }
        }
    }

    /**
     * Looking for OS environment variables that match the properties of the given Kamelet. At first lookup attempt is
     * made without considering camelCase keys in the elements. The second lookup is converting camelCase to
     * underscores.
     *
     * For example given an ENV variable in either format: - CAMEL_KAMELET_AWSS3SOURCE_BUCKETNAMEORARN=myArn -
     * CAMEL_KAMELET_AWS_S3_SOURCE_BUCKET_NAME_OR_ARN=myArn
     */
    public static void extractKameletEnvironmentVariables(Map<String, Object> properties, String... elements) {
        StringBuilder prefixBuffer = new StringBuilder(Kamelet.ENV_VAR_PREFIX);

        // Map contains parameter name as key and full environment variable as value
        Map<String, String> propertyMappings = new HashMap<>();
        for (String element : elements) {
            if (element == null) {
                continue;
            }
            prefixBuffer.append(IOHelper.normalizeEnvironmentVariable(element)).append('_');

            String prefix = prefixBuffer.toString();
            System.getenv().keySet().stream()
                    .filter(Kamelet.startsWith(prefix))
                    .forEach(name -> propertyMappings.put(name.substring(prefix.length()), name));
        }

        prefixBuffer = new StringBuilder(Kamelet.ENV_VAR_PREFIX);

        for (String element : elements) {
            if (element == null) {
                continue;
            }
            prefixBuffer.append(IOHelper.normalizeEnvironmentVariable(StringHelper.camelCaseToDash(element))).append('_');

            String prefix = prefixBuffer.toString();
            System.getenv().keySet().stream()
                    .filter(Kamelet.startsWith(prefix))
                    .forEach(name -> propertyMappings.put(name.substring(prefix.length()), name));
        }

        for (Map.Entry<String, String> mapping : propertyMappings.entrySet()) {
            String value = System.getenv(mapping.getValue());
            if (value != null) {
                properties.put(mapping.getKey(), value);
            }
        }
    }

    public static RouteDefinition templateToRoute(RouteTemplateDefinition in, Map<String, Object> parameters) {
        final String rid = (String) parameters.get(PARAM_ROUTE_ID);
        final boolean noErrorHandler = (boolean) parameters.get(NO_ERROR_HANDLER);
        final String uuid = (String) parameters.get(PARAM_UUID);
        final String pid = (String) parameters.get(PARENT_ROUTE_ID);

        ObjectHelper.notNull(rid, PARAM_ROUTE_ID);
        ObjectHelper.notNull(uuid, PARAM_UUID);

        RouteDefinition def = in.asRouteDefinition();
        // mark this as created from a kamelet
        def.setKamelet(true);
        def.setLocation(in.getLocation());
        def.setLineNumber(in.getLineNumber());
        def.setId(rid);
        def.setNodePrefixId(uuid);
        if (noErrorHandler) {
            def.setErrorHandlerFactory(new NoErrorHandlerBuilder());
        } else if (pid != null) {
            ModelCamelContext mcc = (ModelCamelContext) in.getCamelContext();
            RouteDefinition parent = mcc.getRouteDefinition(pid);
            if (parent != null) {
                def.setErrorHandlerFactory(parent.getErrorHandlerFactory().cloneBuilder());
            }
        }

        if (def.getInput() == null) {
            throw new IllegalArgumentException("Camel route " + rid + " input does not exist.");
        }

        // must make the source and sink endpoints are unique by appending the route id before we create the route from the template
        if (def.getInput().getEndpointUri().startsWith("kamelet:source")
                || def.getInput().getEndpointUri().startsWith("kamelet://source")) {
            def.getInput().setUri("kamelet://source?" + PARAM_ROUTE_ID + "=" + rid);
        }

        // there must be at least one sink
        int line = -1;
        boolean sink = false;
        Collection<ToDefinition> col = filterTypeInOutputs(def.getOutputs(), ToDefinition.class);
        for (ToDefinition to : col) {
            if (to.getEndpointUri().startsWith("kamelet:sink") || to.getEndpointUri().startsWith("kamelet://sink")) {
                to.setUri("kamelet://sink?" + PARAM_ROUTE_ID + "=" + rid);
                sink = true;
            }
            line = to.getLineNumber();
        }
        if (!sink) {
            // this is appended and is used to go back to the kamelet that called me
            ToDefinition to = new ToDefinition("kamelet://sink?" + PARAM_ROUTE_ID + "=" + rid);
            to.setLocation(def.getInput().getLocation());
            if (line != -1) {
                to.setLineNumber(line + 1);
            }
            def.getOutputs().add(to);
        }

        return def;
    }
}
