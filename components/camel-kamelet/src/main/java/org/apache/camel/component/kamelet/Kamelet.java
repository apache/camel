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

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

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
    public static final String PARENT_PROCESSOR_ID = "parentProcessorId";
    public static final String NO_ERROR_HANDLER = "noErrorHandler";
    public static final String BRIDGE_ERROR_HANDLER = "bridgeErrorHandler";
    static final String ADVANCED_SOURCE_OPTIONS = "internal.advanced.source";
    static final String ADVANCED_SINK_OPTIONS = "internal.advanced.sink";

    // use a running counter as uuid
    private static final UuidGenerator UUID = new SimpleUuidGenerator();

    private Kamelet() {
    }

    private static boolean isKameletEndpoint(String uri, String endpoint) {
        if (uri == null) {
            return false;
        }
        return uri.equals("kamelet:" + endpoint)
                || uri.startsWith("kamelet:" + endpoint + "?")
                || uri.startsWith("kamelet:" + endpoint + "/")
                || uri.equals("kamelet://" + endpoint)
                || uri.startsWith("kamelet://" + endpoint + "?")
                || uri.startsWith("kamelet://" + endpoint + "/");
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

    /**
     * Reads the original advanced options map stored under a single internal key.
     */
    private static Map<String, Object> extractAdvancedOptions(Map<String, Object> parameters, String key) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (parameters == null) {
            return options;
        }

        Object value = parameters.get(key);
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    options.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }

        return options;
    }

    /**
     * Preserves the user-supplied option name. When both camelCase and kebab-case forms are present, the kebab-case
     * form is kept so the same option is not appended twice.
     */
    private static Map<String, Object> normalizeParameterNames(Map<String, Object> parameters) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, String> seenOriginal = new HashMap<>();
        Map<String, Boolean> seenIsCanonical = new HashMap<>();

        if (parameters == null) {
            return normalized;
        }

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String originalName = entry.getKey();
            String name = StringHelper.camelCaseToDash(originalName);
            boolean isCanonical = originalName.equals(name);

            String existingOriginal = seenOriginal.get(name);
            if (existingOriginal == null) {
                seenOriginal.put(name, originalName);
                seenIsCanonical.put(name, isCanonical);
                normalized.put(originalName, entry.getValue());
            } else {
                boolean existingIsCanonical = seenIsCanonical.get(name);
                if (isCanonical && !existingIsCanonical) {
                    normalized.remove(existingOriginal);
                    seenOriginal.put(name, originalName);
                    seenIsCanonical.put(name, isCanonical);
                    normalized.put(originalName, entry.getValue());
                }
            }
        }

        return normalized;
    }

    /**
     * Appends query parameters without parsing or re-encoding the endpoint URI. This preserves property placeholders
     * and RAW expressions that may appear in the URI.
     */
    private static String appendParametersToURI(String uri, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return uri;
        }

        String result = uri;

        // First remove all parameters that will be overridden.
        for (String name : parameters.keySet()) {
            result = removeQueryParameter(result, name);
        }

        // Then append the options to the preserved URI.
        StringBuilder sb = new StringBuilder(result);
        char sep = result.indexOf('?') < 0 ? '?' : '&';

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            sb.append(sep)
                    .append(entry.getKey())
                    .append('=')
                    .append(wrapRawIfNeeded(String.valueOf(entry.getValue())));
            sep = '&';
        }

        return sb.toString();
    }

    /**
     * Re-wraps values that contain URI-sensitive characters. RAW wrappers may already have been stripped by
     * {@link URISupport#resolveRawParameterValues(Map)} before options reach this method.
     */
    private static String wrapRawIfNeeded(String value) {
        if (isRawToken(value)) {
            return value;
        }
        if (value.contains("&") || value.contains("=") || value.contains("?")
                || value.contains("+") || value.contains("%")) {
            return "RAW(" + value + ")";
        }

        return value;
    }

    private static boolean isRawToken(String value) {
        return (value.startsWith("RAW(") && value.endsWith(")"))
                || (value.startsWith("RAW{") && value.endsWith("}"));
    }

    /**
     * Removes a query parameter by name, treating camelCase and kebab-case names as aliases.
     */
    private static String removeQueryParameter(String uri, String parameterName) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0) {
            return uri;
        }

        String base = uri.substring(0, queryIndex);
        String query = uri.substring(queryIndex + 1);

        Map<String, Object> params;
        try {
            params = URISupport.parseQuery(query);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI query: " + query, e);
        }

        String normalizedName = StringHelper.camelCaseToDash(parameterName);
        params.keySet().removeIf(
                name -> StringHelper.camelCaseToDash(name).equals(normalizedName));

        return params.isEmpty() ? base : base + "?" + URISupport.createQueryString(params, false);
    }

    /**
     * Finds a single statically defined main sink among top-level outputs.
     *
     * <p>
     * Endpoint schemes are deliberately not used to guess whether a destination is auxiliary. A log or direct endpoint
     * can be a legitimate destination, and scheme-based exclusions can select the wrong endpoint. Only top-level static
     * to() destinations are considered; destinations inside branches and WireTap are not.
     *
     * @throws IllegalArgumentException if more than one candidate exists, because the intended sink cannot be
     *                                  determined unambiguously
     */
    private static ToDefinition findMainSink(Collection<ProcessorDefinition<?>> outputs) {
        if (outputs == null) {
            return null;
        }

        ToDefinition mainSink = null;

        for (ProcessorDefinition<?> output : outputs) {
            if (output instanceof WireTapDefinition) {
                continue;
            }

            if (output instanceof ToDefinition to
                    && to.getEndpointUri() != null
                    && !to.getEndpointUri().startsWith("kamelet:")) {
                if (mainSink != null) {
                    throw new IllegalArgumentException(
                            "sink.* advanced options cannot be applied because the Kamelet has multiple "
                                                       + "top-level static to() destinations. Configure the options directly on the "
                                                       + "intended destination endpoint URI.");
                }
                mainSink = to;
            }
        }

        return mainSink;
    }

    /**
     * Returns whether the processor tree contains a dynamic destination that cannot be configured by appending options
     * to a static to() endpoint. WireTap is excluded because it is an auxiliary destination.
     */
    private static boolean hasUnsupportedDynamicSinkTarget(Collection<ProcessorDefinition<?>> outputs) {
        if (outputs == null) {
            return false;
        }

        for (ProcessorDefinition<?> output : outputs) {
            if (output instanceof WireTapDefinition) {
                continue;
            }
            if (output instanceof ToDynamicDefinition
                    || output instanceof EnrichDefinition
                    || output instanceof PollEnrichDefinition) {
                return true;
            }
            if (hasUnsupportedDynamicSinkTarget(output.getOutputs())) {
                return true;
            }
        }

        return false;
    }

    public static RouteDefinition templateToRoute(RouteTemplateDefinition in, Map<String, Object> parameters) {
        final String rid = (String) parameters.get(PARAM_ROUTE_ID);
        final boolean noErrorHandler = (boolean) parameters.get(NO_ERROR_HANDLER);
        final boolean bridgeErrorHandler = (boolean) parameters.get(BRIDGE_ERROR_HANDLER);
        final String uuid = (String) parameters.get(PARAM_UUID);
        final String prid = (String) parameters.get(PARENT_ROUTE_ID);
        final String ppid = (String) parameters.get(PARENT_PROCESSOR_ID);

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
        } else if (prid != null) {
            ModelCamelContext mcc = (ModelCamelContext) in.getCamelContext();
            RouteDefinition parent = mcc.getRouteDefinition(prid);
            boolean wrap = true;

            // the kamelet are used from a processor, and we need to check if this processor
            // has any error handler or not (if not then we should also not use error handler in the kamelet)
            // (if ppid is null then it is a source kamelet)
            if (ppid != null) {
                ProcessorDefinition<?> pro = mcc.getProcessorDefinition(ppid);
                wrap = pro == null || ProcessorDefinitionHelper.shouldWrapInErrorHandler(
                        mcc, pro, null, CamelContextHelper.parseBoolean(mcc, pro.getInheritErrorHandler()));
            }

            if (wrap && parent != null && parent.isKamelet() == null) {
                // do not wrap if the parent is also a Kamelet
                def.setErrorHandlerFactory(parent.getErrorHandlerFactory().cloneBuilder());
                def.setRouteConfigurationId(parent.getRouteConfigurationId());
            } else {
                def.setErrorHandlerFactory(new NoErrorHandlerBuilder());
            }
        }

        if (def.getInput() == null) {
            throw new IllegalArgumentException("Camel route " + rid + " input does not exist.");
        }

        // Extract advanced options for source/sink component endpoints.
        // The internal.advanced.source and internal.advanced.sink keys are reserved for these option maps.
        Map<String, Object> sourceOptions = normalizeParameterNames(
                extractAdvancedOptions(parameters, ADVANCED_SOURCE_OPTIONS));
        Map<String, Object> sinkOptions = normalizeParameterNames(
                extractAdvancedOptions(parameters, ADVANCED_SINK_OPTIONS));

        // Determine Kamelet type: source Kamelets have a real component as input;
        // sink/action Kamelets have kamelet:source as input and the real component in outputs.
        boolean isSourceKamelet = !isKameletEndpoint(def.getInput().getEndpointUri(), SOURCE_ID);

        if (isSourceKamelet) {
            // Source Kamelet: the real component endpoint is the input.
            if (!sourceOptions.isEmpty()) {
                String uri = def.getInput().getEndpointUri();
                try {
                    def.getInput().setUri(appendParametersToURI(uri, sourceOptions));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Failed to apply advanced source options to endpoint URI: " + uri, e);
                }
            }

            if (!sinkOptions.isEmpty()) {
                throw new IllegalArgumentException(
                        "sink.* advanced options are not valid for source Kamelets (input is '"
                                                   + def.getInput().getEndpointUri() + "'). Use source.* options instead.");
            }
        } else {
            // Sink/action Kamelet: the real component endpoint is in the outputs.
            if (!sourceOptions.isEmpty()) {
                throw new IllegalArgumentException(
                        "source.* advanced options are not valid for sink/action Kamelets (input is '"
                                                   + def.getInput().getEndpointUri() + "'). Use sink.* options instead.");
            }

            if (!sinkOptions.isEmpty()) {
                // If a dynamic destination exists, the intended static destination cannot be determined reliably.
                if (hasUnsupportedDynamicSinkTarget(def.getOutputs())) {
                    throw new IllegalArgumentException(
                            "sink.* advanced options cannot be applied because this Kamelet contains a dynamic "
                                                       + "destination (toD, enrich, or pollEnrich). Configure the options directly "
                                                       + "in the destination endpoint URI.");
                }

                ToDefinition mainSink = findMainSink(def.getOutputs());

                if (mainSink == null) {
                    throw new IllegalArgumentException(
                            "sink.* advanced options cannot be applied because the Kamelet has no top-level "
                                                       + "static to() destination. Configure the options directly on the destination "
                                                       + "endpoint URI.");
                }

                String uri = mainSink.getEndpointUri();
                try {
                    mainSink.setUri(appendParametersToURI(uri, sinkOptions));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Failed to apply advanced sink options to endpoint URI: " + uri, e);
                }
            }
        }

        // Make source and sink endpoints unique by appending the route ID before creating the route from the template.
        if (isKameletEndpoint(def.getInput().getEndpointUri(), SOURCE_ID)) {
            def.getInput().setUri("kamelet://source?" + PARAM_ROUTE_ID + "=" + rid);
        }

        if (bridgeErrorHandler) {
            String uri = def.getInput().getUri();
            if (!uri.contains("bridgeErrorHandler=")) {
                if (uri.contains("?")) {
                    uri = uri + "&bridgeErrorHandler=true";
                } else {
                    uri = uri + "?bridgeErrorHandler=true";
                }
                def.getInput().setUri(uri);
            }
        }

        // there must be at least one sink
        int line = -1;
        boolean sink = false;
        Collection<ToDefinition> col = filterTypeInOutputs(def.getOutputs(), ToDefinition.class);

        for (ToDefinition to : col) {
            if (isKameletEndpoint(to.getEndpointUri(), SINK_ID)) {
                to.setUri("kamelet://sink?" + PARAM_ROUTE_ID + "=" + rid);
                sink = true;
            }
            line = to.getLineNumber();
        }

        if (!sink) {
            // This is appended and is used to go back to the Kamelet that called me.
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
