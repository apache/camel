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

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

public final class Kamelet {
    public static final String PROPERTIES_PREFIX = "camel.kamelet.";
    public static final String SCHEME = "kamelet";
    public static final String SOURCE_ID = "source";
    public static final String SINK_ID = "sink";
    public static final String PARAM_ROUTE_ID = "routeId";
    public static final String PARAM_TEMPLATE_ID = "templateId";

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

    public static String extractRouteId(CamelContext context, String remaining, Map<String, Object> parameters) {
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
            answer = extractTemplateId(context, remaining, parameters) + "-" + context.getUuidGenerator().generateUuid();
        }

        return answer;
    }

    public static void extractKameletProperties(CamelContext context, Map<String, Object> properties, String... elements) {
        PropertiesComponent pc = context.getPropertiesComponent();
        String prefix = Kamelet.PROPERTIES_PREFIX;

        for (String element : elements) {
            if (element == null) {
                continue;
            }

            prefix = prefix + element + ".";

            Properties prefixed = pc.loadProperties(Kamelet.startsWith(prefix));
            for (String name : prefixed.stringPropertyNames()) {
                properties.put(name.substring(prefix.length()), prefixed.getProperty(name));
            }
        }
    }

    public static RouteDefinition templateToRoute(RouteTemplateDefinition in, Map<String, Object> parameters) {
        final String rid = (String) parameters.get(PARAM_ROUTE_ID);

        ObjectHelper.notNull(rid, PARAM_ROUTE_ID);

        RouteDefinition def = in.asRouteDefinition();
        def.setId(rid);

        if (def.getInput() == null) {
            throw new IllegalArgumentException("Camel route " + rid + " input does not exist.");
        }

        // must make the source and sink endpoints are unique by appending the route id before we create the route from the template
        if (def.getInput().getEndpointUri().startsWith("kamelet:source")
                || def.getInput().getEndpointUri().startsWith("kamelet://source")) {
            def.setInput(null);
            def.setInput(new FromDefinition("kamelet:source?" + PARAM_ROUTE_ID + "=" + rid));
        }

        Iterator<ToDefinition> it = filterTypeInOutputs(def.getOutputs(), ToDefinition.class);
        while (it.hasNext()) {
            ToDefinition to = it.next();
            if (to.getEndpointUri().startsWith("kamelet:sink") || to.getEndpointUri().startsWith("kamelet://sink")) {
                to.setUri("kamelet:sink?" + PARAM_ROUTE_ID + "=" + rid);
            }
        }

        return def;
    }
}
