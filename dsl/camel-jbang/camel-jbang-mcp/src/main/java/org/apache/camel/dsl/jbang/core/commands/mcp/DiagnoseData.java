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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Shared holder for Camel exception diagnostic reference data used by both {@link DiagnoseTools} (MCP Tool) and
 * {@link DiagnoseResources} (MCP Resources).
 * <p>
 * Contains the registry of known Camel exceptions with their descriptions, common causes, suggested fixes, and
 * documentation links.
 */
@ApplicationScoped
public class DiagnoseData {

    public static final String CAMEL_DOC_BASE = "https://camel.apache.org/";
    public static final String CAMEL_COMPONENT_DOC = CAMEL_DOC_BASE + "components/next/";
    public static final String CAMEL_MANUAL_DOC = CAMEL_DOC_BASE + "manual/";
    public static final String CAMEL_EIP_DOC = CAMEL_COMPONENT_DOC + "eips/";

    /**
     * Component doc base URL for a specific Camel version.
     *
     * @param version the version segment (e.g., "4.18.x", "4.14.x"), or null/"next" for the latest development docs
     */
    public static String componentDocBase(String version) {
        String v = (version == null || version.isBlank() || "next".equals(version)) ? "next" : version;
        return CAMEL_DOC_BASE + "components/" + v + "/";
    }

    /**
     * EIP doc base URL for a specific Camel version.
     */
    public static String eipDocBase(String version) {
        return componentDocBase(version) + "eips/";
    }

    /**
     * Resolve documentation links for a specific version by replacing the "next" version segment in component/EIP doc
     * URLs with the specified version. Manual URLs (which have no version segment) are left unchanged.
     *
     * @param links   the original documentation links (using "next")
     * @param version the target version (e.g., "4.18.x"), or null/"next" to keep defaults
     */
    public static List<String> resolveDocLinks(List<String> links, String version) {
        if (version == null || version.isBlank() || "next".equals(version)) {
            return links;
        }
        List<String> resolved = new ArrayList<>(links.size());
        for (String link : links) {
            resolved.add(link.replace("components/next/", "components/" + version + "/"));
        }
        return resolved;
    }

    private static final Map<String, ExceptionInfo> KNOWN_EXCEPTIONS;

    static {
        Map<String, ExceptionInfo> exceptions = new LinkedHashMap<>();

        exceptions.put("NoSuchEndpointException", new ExceptionInfo(
                "The specified endpoint URI could not be resolved to any known Camel component.",
                List.of(
                        "Typo in the endpoint URI scheme (e.g., 'kafak:' instead of 'kafka:')",
                        "Missing component dependency in pom.xml or build.gradle",
                        "Component not on the classpath",
                        "Using a component scheme that does not exist"),
                List.of(
                        "Verify the endpoint URI scheme is spelled correctly",
                        "Add the required camel-<component> dependency to your project",
                        "Check available components with 'camel-catalog' or the Camel documentation",
                        "Ensure the component JAR is on the classpath"),
                List.of(
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("ResolveEndpointFailedException", new ExceptionInfo(
                "Failed to resolve or create an endpoint from the given URI. The URI syntax may be invalid or required options may be missing.",
                List.of(
                        "Invalid endpoint URI syntax",
                        "Missing required endpoint options",
                        "Unknown or misspelled endpoint options",
                        "Invalid option values (wrong type or format)",
                        "Special characters in URI not properly encoded"),
                List.of(
                        "Check the endpoint URI syntax against the component documentation",
                        "Ensure all required options are provided",
                        "Verify option names are spelled correctly",
                        "URL-encode special characters in the URI",
                        "Use the endpoint DSL for type-safe endpoint configuration"),
                List.of(
                        CAMEL_MANUAL_DOC + "endpoint.html",
                        CAMEL_MANUAL_DOC + "uris.html")));

        exceptions.put("FailedToCreateRouteException", new ExceptionInfo(
                "A route could not be created. This is typically a configuration or wiring issue.",
                List.of(
                        "Invalid endpoint URI in from() or to()",
                        "Missing required component dependency",
                        "Bean reference that cannot be resolved",
                        "Invalid route configuration or DSL syntax",
                        "Circular route dependencies"),
                List.of(
                        "Check the full exception chain for the root cause",
                        "Verify all endpoint URIs in the route are valid",
                        "Ensure all referenced beans are available in the registry",
                        "Validate the route DSL syntax",
                        "Check for missing component dependencies"),
                List.of(
                        CAMEL_MANUAL_DOC + "routes.html",
                        CAMEL_MANUAL_DOC + "route-configuration.html")));

        exceptions.put("FailedToStartRouteException", new ExceptionInfo(
                "A route was created but could not be started. This often indicates a connectivity or resource issue.",
                List.of(
                        "Cannot connect to external service (broker, database, etc.)",
                        "Port already in use for server-side components",
                        "Authentication/authorization failure",
                        "Missing or invalid SSL/TLS configuration",
                        "Resource not available (queue, topic, table, etc.)"),
                List.of(
                        "Verify network connectivity to external services",
                        "Check credentials and authentication configuration",
                        "Ensure the target resource exists (queue, topic, etc.)",
                        "Review SSL/TLS configuration if using secure connections",
                        "Check if the port is already in use"),
                List.of(
                        CAMEL_MANUAL_DOC + "routes.html",
                        CAMEL_MANUAL_DOC + "lifecycle.html")));

        exceptions.put("NoTypeConversionAvailableException", new ExceptionInfo(
                "Camel could not find a type converter to convert between the required types.",
                List.of(
                        "Trying to convert a message body to an incompatible type",
                        "Missing type converter on the classpath",
                        "Custom type without a registered converter",
                        "Null body when a non-null type is expected"),
                List.of(
                        "Check the source and target types in the conversion",
                        "Add appropriate data format or converter dependency",
                        "Use explicit marshal/unmarshal instead of implicit conversion",
                        "Register a custom TypeConverter if needed",
                        "Check if the message body is null"),
                List.of(
                        CAMEL_MANUAL_DOC + "type-converter.html",
                        CAMEL_MANUAL_DOC + "data-format.html")));

        exceptions.put("CamelExecutionException", new ExceptionInfo(
                "A wrapper exception thrown during route execution. The root cause is in the nested exception.",
                List.of(
                        "Exception thrown by a processor or bean in the route",
                        "External service failure (HTTP error, broker disconnect, etc.)",
                        "Data transformation error",
                        "Timeout during synchronous processing"),
                List.of(
                        "Inspect the nested/caused-by exception for the actual error",
                        "Add error handling (onException, errorHandler, doTry/doCatch) to the route",
                        "Check the processor or bean that failed",
                        "Review the full stack trace for the root cause"),
                List.of(
                        CAMEL_MANUAL_DOC + "exception-clause.html",
                        CAMEL_MANUAL_DOC + "error-handler.html",
                        CAMEL_MANUAL_DOC + "try-catch-finally.html")));

        exceptions.put("ExchangeTimedOutException", new ExceptionInfo(
                "An exchange did not complete within the configured timeout period.",
                List.of(
                        "Slow downstream service or endpoint",
                        "Network latency or connectivity issues",
                        "Timeout value too low for the operation",
                        "Deadlock or resource contention",
                        "Direct/SEDA consumer not available"),
                List.of(
                        "Increase the timeout value if appropriate",
                        "Add circuit breaker pattern for unreliable services",
                        "Check network connectivity to the target service",
                        "Use async processing for long-running operations",
                        "Add timeout error handling in the route"),
                List.of(
                        CAMEL_MANUAL_DOC + "request-reply.html",
                        CAMEL_EIP_DOC + "circuitBreaker-eip.html")));

        exceptions.put("DirectConsumerNotAvailableException", new ExceptionInfo(
                "No consumer is available for a direct endpoint. The direct component requires an active consumer.",
                List.of(
                        "Target route with the direct endpoint is not started",
                        "Typo in the direct endpoint name",
                        "Route with the direct consumer was stopped or removed",
                        "Timing issue during startup — producer route started before consumer route"),
                List.of(
                        "Ensure a route with from(\"direct:name\") exists and is started",
                        "Verify the direct endpoint name matches between producer and consumer",
                        "Use SEDA instead of direct if startup ordering is uncertain",
                        "Configure route startup ordering if needed"),
                List.of(
                        CAMEL_COMPONENT_DOC + "direct-component.html",
                        CAMEL_COMPONENT_DOC + "seda-component.html")));

        exceptions.put("CamelExchangeException", new ExceptionInfo(
                "A general exception related to exchange processing.",
                List.of(
                        "Processor failure during exchange handling",
                        "Invalid exchange pattern (InOnly vs InOut mismatch)",
                        "Missing required headers or properties",
                        "Exchange body cannot be processed"),
                List.of(
                        "Check the exchange pattern matches the endpoint requirements",
                        "Verify required headers are set on the exchange",
                        "Add error handling to catch and process the exception",
                        "Inspect the exchange body type and content"),
                List.of(
                        CAMEL_MANUAL_DOC + "exchange.html",
                        CAMEL_MANUAL_DOC + "exchange-pattern.html")));

        exceptions.put("InvalidPayloadException", new ExceptionInfo(
                "The message payload (body) is not of the expected type and cannot be converted.",
                List.of(
                        "Message body is null when a value is expected",
                        "Message body type does not match the expected type",
                        "Missing type converter for the body type",
                        "Upstream processor produced unexpected output"),
                List.of(
                        "Check the message body type before the failing processor",
                        "Use convertBodyTo() to explicitly convert the body type",
                        "Add a null check or default value for the body",
                        "Add the appropriate data format dependency for marshalling/unmarshalling"),
                List.of(
                        CAMEL_MANUAL_DOC + "message.html",
                        CAMEL_MANUAL_DOC + "type-converter.html")));

        exceptions.put("PropertyBindingException", new ExceptionInfo(
                "Failed to bind a property or option to a Camel component, endpoint, or bean.",
                List.of(
                        "Property name does not exist on the target object",
                        "Property value has wrong type (e.g., string for a boolean)",
                        "Misspelled property or option name",
                        "Property placeholder could not be resolved"),
                List.of(
                        "Check the property name spelling against the component documentation",
                        "Verify the property value type matches the expected type",
                        "Use property placeholders correctly: {{property.name}}",
                        "Check application.properties or YAML configuration for correct keys"),
                List.of(
                        CAMEL_MANUAL_DOC + "using-propertyplaceholder.html",
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("NoSuchBeanException", new ExceptionInfo(
                "A referenced bean could not be found in the Camel registry.",
                List.of(
                        "Bean not registered in the Spring/CDI/Camel registry",
                        "Typo in the bean name reference",
                        "Bean class not on the classpath",
                        "Missing @Named or @Component annotation on the bean class",
                        "Bean definition not scanned by component scan"),
                List.of(
                        "Verify the bean is registered with the correct name",
                        "Check spelling of the bean reference",
                        "Ensure the bean class has proper annotations (@Named, @Component, etc.)",
                        "Verify component scanning includes the bean's package",
                        "Register the bean manually in RouteBuilder configure() if needed"),
                List.of(
                        CAMEL_MANUAL_DOC + "registry.html",
                        CAMEL_MANUAL_DOC + "bean-binding.html")));

        exceptions.put("NoSuchHeaderException", new ExceptionInfo(
                "A required header was not found on the message.",
                List.of(
                        "Header not set by upstream processors",
                        "Header name is misspelled",
                        "Header was removed by a previous processor",
                        "Using wrong header constant name"),
                List.of(
                        "Verify the header name matches what upstream processors set",
                        "Use header constants from the component's class (e.g., KafkaConstants)",
                        "Add a setHeader() before the processor that requires it",
                        "Add a null check or default value for the header"),
                List.of(
                        CAMEL_MANUAL_DOC + "message.html")));

        exceptions.put("PredicateValidationException", new ExceptionInfo(
                "A predicate validation failed. This is typically thrown by the validate() DSL or a filter condition.",
                List.of(
                        "Message did not match the expected validation predicate",
                        "Invalid or unexpected message content",
                        "Predicate expression has a syntax error",
                        "Null values in the expression evaluation"),
                List.of(
                        "Review the predicate expression for correctness",
                        "Check the message content matches the expected format",
                        "Add error handling for validation failures",
                        "Use a more lenient predicate or add default values"),
                List.of(
                        CAMEL_EIP_DOC + "validate-eip.html",
                        CAMEL_MANUAL_DOC + "predicate.html")));

        exceptions.put("NoSuchLanguageException", new ExceptionInfo(
                "The specified expression language is not available.",
                List.of(
                        "Missing language dependency (e.g., camel-jsonpath, camel-xpath)",
                        "Typo in the language name",
                        "Using a language that does not exist"),
                List.of(
                        "Add the required camel-<language> dependency",
                        "Verify the language name is spelled correctly",
                        "Use 'simple' language which is included in camel-core"),
                List.of(
                        CAMEL_MANUAL_DOC + "languages.html")));

        exceptions.put("FailedToCreateConsumerException", new ExceptionInfo(
                "A consumer could not be created for the endpoint.",
                List.of(
                        "Cannot connect to the source system (broker, server, etc.)",
                        "Invalid consumer configuration options",
                        "Authentication failure",
                        "Missing required consumer options",
                        "Resource does not exist (topic, queue, file path, etc.)"),
                List.of(
                        "Verify connectivity to the source system",
                        "Check the consumer configuration options",
                        "Ensure credentials are correct",
                        "Verify the target resource exists",
                        "Check the full exception chain for the root cause"),
                List.of(
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("FailedToCreateProducerException", new ExceptionInfo(
                "A producer could not be created for the endpoint.",
                List.of(
                        "Cannot connect to the target system",
                        "Invalid producer configuration options",
                        "Authentication failure",
                        "Missing required producer options"),
                List.of(
                        "Verify connectivity to the target system",
                        "Check the producer configuration options",
                        "Ensure credentials are correct",
                        "Check the full exception chain for the root cause"),
                List.of(
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("CamelAuthorizationException", new ExceptionInfo(
                "An authorization check failed. The current identity does not have permission to perform the operation.",
                List.of(
                        "Insufficient permissions for the user or service account",
                        "Missing or expired authentication token",
                        "Security policy denying the operation",
                        "Incorrect RBAC or ACL configuration"),
                List.of(
                        "Check the user/service account permissions",
                        "Verify the authentication token is valid and not expired",
                        "Review the security policies and ACLs",
                        "Ensure the correct security provider is configured"),
                List.of(
                        CAMEL_MANUAL_DOC + "security.html")));

        KNOWN_EXCEPTIONS = Collections.unmodifiableMap(exceptions);
    }

    /**
     * Get all known Camel exceptions with their diagnostic information.
     */
    public Map<String, ExceptionInfo> getKnownExceptions() {
        return KNOWN_EXCEPTIONS;
    }

    /**
     * Get diagnostic information for a specific exception by name.
     *
     * @return the exception info, or null if not found
     */
    public ExceptionInfo getException(String name) {
        return KNOWN_EXCEPTIONS.get(name);
    }

    /**
     * Get all known exception names.
     */
    public List<String> getExceptionNames() {
        return List.copyOf(KNOWN_EXCEPTIONS.keySet());
    }

    /**
     * Holds diagnostic information about a known Camel exception.
     */
    public record ExceptionInfo(
            String description,
            List<String> commonCauses,
            List<String> suggestedFixes,
            List<String> documentationLinks) {

        /**
         * Convert this exception info to a full JSON object with all fields.
         */
        public JsonObject toJson() {
            return toJson(null);
        }

        /**
         * Convert this exception info to a full JSON object, resolving doc links for the given version.
         *
         * @param version the Camel doc version (e.g., "4.18.x"), or null for "next"
         */
        public JsonObject toJson(String version) {
            JsonObject json = new JsonObject();
            json.put("description", description);
            json.put("commonCauses", toJsonArray(commonCauses));
            json.put("suggestedFixes", toJsonArray(suggestedFixes));
            json.put("documentationLinks", toJsonArray(resolveDocLinks(documentationLinks, version)));
            return json;
        }

        /**
         * Convert this exception info to a summary JSON object (description and doc links only).
         */
        public JsonObject toSummaryJson() {
            return toSummaryJson(null);
        }

        /**
         * Convert this exception info to a summary JSON object, resolving doc links for the given version.
         *
         * @param version the Camel doc version (e.g., "4.18.x"), or null for "next"
         */
        public JsonObject toSummaryJson(String version) {
            JsonObject json = new JsonObject();
            json.put("description", description);
            json.put("documentationLinks", toJsonArray(resolveDocLinks(documentationLinks, version)));
            return json;
        }

        private static JsonArray toJsonArray(List<String> items) {
            JsonArray array = new JsonArray();
            for (String item : items) {
                array.add(item);
            }
            return array;
        }
    }
}
