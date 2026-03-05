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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for diagnosing Camel errors from stack traces or error messages.
 * <p>
 * Accepts a Camel stack trace or error message and returns the likely component/EIP involved, common causes, links to
 * relevant documentation, and suggested fixes.
 */
@ApplicationScoped
public class DiagnoseTools {

    private static final String CAMEL_DOC_BASE = "https://camel.apache.org/";
    private static final String CAMEL_COMPONENT_DOC = CAMEL_DOC_BASE + "components/next/";
    private static final String CAMEL_MANUAL_DOC = CAMEL_DOC_BASE + "manual/";
    private static final String CAMEL_EIP_DOC = CAMEL_COMPONENT_DOC + "eips/";

    /**
     * Pattern to extract component names from endpoint URIs in error messages (e.g., "kafka:myTopic", "http://host").
     */
    private static final Pattern ENDPOINT_URI_PATTERN = Pattern
            .compile("(?:endpoint|uri)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?[^\\s'\"]*", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to extract component scheme from common error contexts.
     */
    private static final Pattern COMPONENT_SCHEME_PATTERN
            = Pattern.compile("(?:component|scheme)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*)['\"]?", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to extract route IDs from error messages.
     */
    private static final Pattern ROUTE_ID_PATTERN
            = Pattern.compile("route[:\\s]+['\"]?([a-zA-Z0-9_-]+)['\"]?", Pattern.CASE_INSENSITIVE);

    private final CamelCatalog catalog;
    private final Map<String, ExceptionInfo> knownExceptions;

    public DiagnoseTools() {
        this.catalog = new DefaultCamelCatalog();
        this.knownExceptions = buildKnownExceptions();
    }

    /**
     * Tool to diagnose Camel errors from stack traces or error messages.
     */
    @Tool(description = "Diagnose a Camel error from a stack trace or error message. "
                        + "Returns the identified component/EIP involved, common causes for the error, "
                        + "links to relevant Camel documentation, and suggested fixes. "
                        + "Covers the most common Camel exceptions including NoSuchEndpointException, "
                        + "ResolveEndpointFailedException, FailedToCreateRouteException, and more.")
    public String camel_error_diagnose(
            @ToolArg(description = "The Camel stack trace or error message to diagnose") String error) {

        if (error == null || error.isBlank()) {
            throw new ToolCallException("Error message or stack trace is required", null);
        }

        try {
            JsonObject result = new JsonObject();

            // Identify matching exceptions
            List<String> matchedExceptions = identifyExceptions(error);
            JsonArray exceptionsJson = new JsonArray();
            for (String exceptionName : matchedExceptions) {
                ExceptionInfo info = knownExceptions.get(exceptionName);
                if (info != null) {
                    JsonObject exJson = new JsonObject();
                    exJson.put("exception", exceptionName);
                    exJson.put("description", info.description);

                    JsonArray causesJson = new JsonArray();
                    for (String cause : info.commonCauses) {
                        causesJson.add(cause);
                    }
                    exJson.put("commonCauses", causesJson);

                    JsonArray fixesJson = new JsonArray();
                    for (String fix : info.suggestedFixes) {
                        fixesJson.add(fix);
                    }
                    exJson.put("suggestedFixes", fixesJson);

                    JsonArray docsJson = new JsonArray();
                    for (String doc : info.documentationLinks) {
                        docsJson.add(doc);
                    }
                    exJson.put("documentationLinks", docsJson);

                    exceptionsJson.add(exJson);
                }
            }
            result.put("identifiedExceptions", exceptionsJson);

            // Identify components from the error
            List<String> componentNames = extractComponentNames(error);
            JsonArray componentsJson = new JsonArray();
            for (String comp : componentNames) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    JsonObject compJson = new JsonObject();
                    compJson.put("name", comp);
                    compJson.put("title", model.getTitle());
                    compJson.put("description", model.getDescription());
                    compJson.put("documentationUrl", CAMEL_COMPONENT_DOC + comp + "-component.html");
                    componentsJson.add(compJson);
                }
            }
            result.put("identifiedComponents", componentsJson);

            // Identify EIPs from the error
            List<String> eipNames = extractEipNames(error);
            JsonArray eipsJson = new JsonArray();
            for (String eip : eipNames) {
                EipModel model = catalog.eipModel(eip);
                if (model != null) {
                    JsonObject eipJson = new JsonObject();
                    eipJson.put("name", eip);
                    eipJson.put("title", model.getTitle());
                    eipJson.put("description", model.getDescription());
                    eipJson.put("documentationUrl", CAMEL_EIP_DOC + eip + "-eip.html");
                    eipsJson.add(eipJson);
                }
            }
            result.put("identifiedEips", eipsJson);

            // Extract route ID if present
            Matcher routeMatcher = ROUTE_ID_PATTERN.matcher(error);
            if (routeMatcher.find()) {
                result.put("routeId", routeMatcher.group(1));
            }

            // Summary
            JsonObject summary = new JsonObject();
            summary.put("exceptionCount", exceptionsJson.size());
            summary.put("componentCount", componentsJson.size());
            summary.put("eipCount", eipsJson.size());
            summary.put("diagnosed", !exceptionsJson.isEmpty());
            result.put("summary", summary);

            return result.toJson();
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to diagnose error (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Identify known Camel exceptions in the error text.
     */
    private List<String> identifyExceptions(String error) {
        List<String> matched = new ArrayList<>();
        for (String exceptionName : knownExceptions.keySet()) {
            if (error.contains(exceptionName)) {
                matched.add(exceptionName);
            }
        }
        return matched;
    }

    /**
     * Extract component names from endpoint URIs and other patterns in the error text.
     */
    private List<String> extractComponentNames(String error) {
        List<String> found = new ArrayList<>();

        // Try endpoint URI pattern
        Matcher uriMatcher = ENDPOINT_URI_PATTERN.matcher(error);
        while (uriMatcher.find()) {
            String scheme = uriMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }

        // Try component scheme pattern
        Matcher schemeMatcher = COMPONENT_SCHEME_PATTERN.matcher(error);
        while (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }

        // Scan for known component names in the error text
        String lowerError = error.toLowerCase();
        for (String comp : catalog.findComponentNames()) {
            if (!found.contains(comp) && containsComponent(lowerError, comp)) {
                found.add(comp);
            }
        }

        return found;
    }

    /**
     * Extract EIP names from the error text.
     */
    private List<String> extractEipNames(String error) {
        List<String> found = new ArrayList<>();
        String lowerError = error.toLowerCase();

        for (String eip : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(eip);
            if (model != null) {
                String eipLower = eip.toLowerCase();
                if (lowerError.contains(eipLower) && !found.contains(eip)) {
                    found.add(eip);
                }
            }
        }

        return found;
    }

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
    }

    /**
     * Build the registry of known Camel exceptions with their causes, fixes, and documentation.
     */
    private Map<String, ExceptionInfo> buildKnownExceptions() {
        Map<String, ExceptionInfo> exceptions = new LinkedHashMap<>();

        exceptions.put("NoSuchEndpointException", new ExceptionInfo(
                "The specified endpoint URI could not be resolved to any known Camel component.",
                Arrays.asList(
                        "Typo in the endpoint URI scheme (e.g., 'kafak:' instead of 'kafka:')",
                        "Missing component dependency in pom.xml or build.gradle",
                        "Component not on the classpath",
                        "Using a component scheme that does not exist"),
                Arrays.asList(
                        "Verify the endpoint URI scheme is spelled correctly",
                        "Add the required camel-<component> dependency to your project",
                        "Check available components with 'camel-catalog' or the Camel documentation",
                        "Ensure the component JAR is on the classpath"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "component.html",
                        CAMEL_MANUAL_DOC + "faq/why-is-my-message-body-empty.html")));

        exceptions.put("ResolveEndpointFailedException", new ExceptionInfo(
                "Failed to resolve or create an endpoint from the given URI. The URI syntax may be invalid or required options may be missing.",
                Arrays.asList(
                        "Invalid endpoint URI syntax",
                        "Missing required endpoint options",
                        "Unknown or misspelled endpoint options",
                        "Invalid option values (wrong type or format)",
                        "Special characters in URI not properly encoded"),
                Arrays.asList(
                        "Check the endpoint URI syntax against the component documentation",
                        "Ensure all required options are provided",
                        "Verify option names are spelled correctly",
                        "URL-encode special characters in the URI",
                        "Use the endpoint DSL for type-safe endpoint configuration"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "endpoint.html",
                        CAMEL_MANUAL_DOC + "uris.html")));

        exceptions.put("FailedToCreateRouteException", new ExceptionInfo(
                "A route could not be created. This is typically a configuration or wiring issue.",
                Arrays.asList(
                        "Invalid endpoint URI in from() or to()",
                        "Missing required component dependency",
                        "Bean reference that cannot be resolved",
                        "Invalid route configuration or DSL syntax",
                        "Circular route dependencies"),
                Arrays.asList(
                        "Check the full exception chain for the root cause",
                        "Verify all endpoint URIs in the route are valid",
                        "Ensure all referenced beans are available in the registry",
                        "Validate the route DSL syntax",
                        "Check for missing component dependencies"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "routes.html",
                        CAMEL_MANUAL_DOC + "route-configuration.html")));

        exceptions.put("FailedToStartRouteException", new ExceptionInfo(
                "A route was created but could not be started. This often indicates a connectivity or resource issue.",
                Arrays.asList(
                        "Cannot connect to external service (broker, database, etc.)",
                        "Port already in use for server-side components",
                        "Authentication/authorization failure",
                        "Missing or invalid SSL/TLS configuration",
                        "Resource not available (queue, topic, table, etc.)"),
                Arrays.asList(
                        "Verify network connectivity to external services",
                        "Check credentials and authentication configuration",
                        "Ensure the target resource exists (queue, topic, etc.)",
                        "Review SSL/TLS configuration if using secure connections",
                        "Check if the port is already in use"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "routes.html",
                        CAMEL_MANUAL_DOC + "lifecycle.html")));

        exceptions.put("NoTypeConversionAvailableException", new ExceptionInfo(
                "Camel could not find a type converter to convert between the required types.",
                Arrays.asList(
                        "Trying to convert a message body to an incompatible type",
                        "Missing type converter on the classpath",
                        "Custom type without a registered converter",
                        "Null body when a non-null type is expected"),
                Arrays.asList(
                        "Check the source and target types in the conversion",
                        "Add appropriate data format or converter dependency",
                        "Use explicit marshal/unmarshal instead of implicit conversion",
                        "Register a custom TypeConverter if needed",
                        "Check if the message body is null"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "type-converter.html",
                        CAMEL_MANUAL_DOC + "data-format.html")));

        exceptions.put("CamelExecutionException", new ExceptionInfo(
                "A wrapper exception thrown during route execution. The root cause is in the nested exception.",
                Arrays.asList(
                        "Exception thrown by a processor or bean in the route",
                        "External service failure (HTTP error, broker disconnect, etc.)",
                        "Data transformation error",
                        "Timeout during synchronous processing"),
                Arrays.asList(
                        "Inspect the nested/caused-by exception for the actual error",
                        "Add error handling (onException, errorHandler, doTry/doCatch) to the route",
                        "Check the processor or bean that failed",
                        "Review the full stack trace for the root cause"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "exception-clause.html",
                        CAMEL_MANUAL_DOC + "error-handler.html",
                        CAMEL_MANUAL_DOC + "try-catch-finally.html")));

        exceptions.put("ExchangeTimedOutException", new ExceptionInfo(
                "An exchange did not complete within the configured timeout period.",
                Arrays.asList(
                        "Slow downstream service or endpoint",
                        "Network latency or connectivity issues",
                        "Timeout value too low for the operation",
                        "Deadlock or resource contention",
                        "Direct/SEDA consumer not available"),
                Arrays.asList(
                        "Increase the timeout value if appropriate",
                        "Add circuit breaker pattern for unreliable services",
                        "Check network connectivity to the target service",
                        "Use async processing for long-running operations",
                        "Add timeout error handling in the route"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "request-reply.html",
                        CAMEL_EIP_DOC + "circuitBreaker-eip.html")));

        exceptions.put("DirectConsumerNotAvailableException", new ExceptionInfo(
                "No consumer is available for a direct endpoint. The direct component requires an active consumer.",
                Arrays.asList(
                        "Target route with the direct endpoint is not started",
                        "Typo in the direct endpoint name",
                        "Route with the direct consumer was stopped or removed",
                        "Timing issue during startup — producer route started before consumer route"),
                Arrays.asList(
                        "Ensure a route with from(\"direct:name\") exists and is started",
                        "Verify the direct endpoint name matches between producer and consumer",
                        "Use SEDA instead of direct if startup ordering is uncertain",
                        "Configure route startup ordering if needed"),
                Arrays.asList(
                        CAMEL_COMPONENT_DOC + "direct-component.html",
                        CAMEL_COMPONENT_DOC + "seda-component.html")));

        exceptions.put("CamelExchangeException", new ExceptionInfo(
                "A general exception related to exchange processing.",
                Arrays.asList(
                        "Processor failure during exchange handling",
                        "Invalid exchange pattern (InOnly vs InOut mismatch)",
                        "Missing required headers or properties",
                        "Exchange body cannot be processed"),
                Arrays.asList(
                        "Check the exchange pattern matches the endpoint requirements",
                        "Verify required headers are set on the exchange",
                        "Add error handling to catch and process the exception",
                        "Inspect the exchange body type and content"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "exchange.html",
                        CAMEL_MANUAL_DOC + "exchange-pattern.html")));

        exceptions.put("InvalidPayloadException", new ExceptionInfo(
                "The message payload (body) is not of the expected type and cannot be converted.",
                Arrays.asList(
                        "Message body is null when a value is expected",
                        "Message body type does not match the expected type",
                        "Missing type converter for the body type",
                        "Upstream processor produced unexpected output"),
                Arrays.asList(
                        "Check the message body type before the failing processor",
                        "Use convertBodyTo() to explicitly convert the body type",
                        "Add a null check or default value for the body",
                        "Add the appropriate data format dependency for marshalling/unmarshalling"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "message.html",
                        CAMEL_MANUAL_DOC + "type-converter.html")));

        exceptions.put("PropertyBindingException", new ExceptionInfo(
                "Failed to bind a property or option to a Camel component, endpoint, or bean.",
                Arrays.asList(
                        "Property name does not exist on the target object",
                        "Property value has wrong type (e.g., string for a boolean)",
                        "Misspelled property or option name",
                        "Property placeholder could not be resolved"),
                Arrays.asList(
                        "Check the property name spelling against the component documentation",
                        "Verify the property value type matches the expected type",
                        "Use property placeholders correctly: {{property.name}}",
                        "Check application.properties or YAML configuration for correct keys"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "using-propertyplaceholder.html",
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("NoSuchBeanException", new ExceptionInfo(
                "A referenced bean could not be found in the Camel registry.",
                Arrays.asList(
                        "Bean not registered in the Spring/CDI/Camel registry",
                        "Typo in the bean name reference",
                        "Bean class not on the classpath",
                        "Missing @Named or @Component annotation on the bean class",
                        "Bean definition not scanned by component scan"),
                Arrays.asList(
                        "Verify the bean is registered with the correct name",
                        "Check spelling of the bean reference",
                        "Ensure the bean class has proper annotations (@Named, @Component, etc.)",
                        "Verify component scanning includes the bean's package",
                        "Register the bean manually in RouteBuilder configure() if needed"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "registry.html",
                        CAMEL_MANUAL_DOC + "bean-binding.html")));

        exceptions.put("NoSuchHeaderException", new ExceptionInfo(
                "A required header was not found on the message.",
                Arrays.asList(
                        "Header not set by upstream processors",
                        "Header name is misspelled",
                        "Header was removed by a previous processor",
                        "Using wrong header constant name"),
                Arrays.asList(
                        "Verify the header name matches what upstream processors set",
                        "Use header constants from the component's class (e.g., KafkaConstants)",
                        "Add a setHeader() before the processor that requires it",
                        "Add a null check or default value for the header"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "message.html")));

        exceptions.put("PredicateValidationException", new ExceptionInfo(
                "A predicate validation failed. This is typically thrown by the validate() DSL or a filter condition.",
                Arrays.asList(
                        "Message did not match the expected validation predicate",
                        "Invalid or unexpected message content",
                        "Predicate expression has a syntax error",
                        "Null values in the expression evaluation"),
                Arrays.asList(
                        "Review the predicate expression for correctness",
                        "Check the message content matches the expected format",
                        "Add error handling for validation failures",
                        "Use a more lenient predicate or add default values"),
                Arrays.asList(
                        CAMEL_EIP_DOC + "validate-eip.html",
                        CAMEL_MANUAL_DOC + "predicate.html")));

        exceptions.put("NoSuchLanguageException", new ExceptionInfo(
                "The specified expression language is not available.",
                Arrays.asList(
                        "Missing language dependency (e.g., camel-jsonpath, camel-xpath)",
                        "Typo in the language name",
                        "Using a language that does not exist"),
                Arrays.asList(
                        "Add the required camel-<language> dependency",
                        "Verify the language name is spelled correctly",
                        "Use 'simple' language which is included in camel-core"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "languages.html")));

        exceptions.put("FailedToCreateConsumerException", new ExceptionInfo(
                "A consumer could not be created for the endpoint.",
                Arrays.asList(
                        "Cannot connect to the source system (broker, server, etc.)",
                        "Invalid consumer configuration options",
                        "Authentication failure",
                        "Missing required consumer options",
                        "Resource does not exist (topic, queue, file path, etc.)"),
                Arrays.asList(
                        "Verify connectivity to the source system",
                        "Check the consumer configuration options",
                        "Ensure credentials are correct",
                        "Verify the target resource exists",
                        "Check the full exception chain for the root cause"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("FailedToCreateProducerException", new ExceptionInfo(
                "A producer could not be created for the endpoint.",
                Arrays.asList(
                        "Cannot connect to the target system",
                        "Invalid producer configuration options",
                        "Authentication failure",
                        "Missing required producer options"),
                Arrays.asList(
                        "Verify connectivity to the target system",
                        "Check the producer configuration options",
                        "Ensure credentials are correct",
                        "Check the full exception chain for the root cause"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "component.html")));

        exceptions.put("CamelAuthorizationException", new ExceptionInfo(
                "An authorization check failed. The current identity does not have permission to perform the operation.",
                Arrays.asList(
                        "Insufficient permissions for the user or service account",
                        "Missing or expired authentication token",
                        "Security policy denying the operation",
                        "Incorrect RBAC or ACL configuration"),
                Arrays.asList(
                        "Check the user/service account permissions",
                        "Verify the authentication token is valid and not expired",
                        "Review the security policies and ACLs",
                        "Ensure the correct security provider is configured"),
                Arrays.asList(
                        CAMEL_MANUAL_DOC + "security.html")));

        return exceptions;
    }

    /**
     * Holds diagnostic information about a known Camel exception.
     */
    static class ExceptionInfo {
        final String description;
        final List<String> commonCauses;
        final List<String> suggestedFixes;
        final List<String> documentationLinks;

        ExceptionInfo(String description, List<String> commonCauses, List<String> suggestedFixes,
                      List<String> documentationLinks) {
            this.description = description;
            this.commonCauses = commonCauses;
            this.suggestedFixes = suggestedFixes;
            this.documentationLinks = documentationLinks;
        }
    }
}
