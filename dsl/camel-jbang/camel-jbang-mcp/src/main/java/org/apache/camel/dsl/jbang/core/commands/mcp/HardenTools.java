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
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for providing security hardening context and analysis for Camel routes.
 * <p>
 * This tool analyzes routes for security-sensitive components, identifies potential vulnerabilities, and provides
 * structured context that an LLM can use to formulate security hardening recommendations.
 */
@ApplicationScoped
public class HardenTools {

    @Inject
    SecurityData securityData;

    private final CamelCatalog catalog;

    public HardenTools() {
        this.catalog = new DefaultCamelCatalog();
    }

    /**
     * Tool to get security hardening context for a Camel route.
     */
    @Tool(description = "Get security hardening analysis context for a Camel route. " +
                        "Returns security-sensitive components, potential vulnerabilities, " +
                        "and security best practices. Use this context to provide security " +
                        "hardening recommendations for the route.")
    public String camel_route_harden_context(
            @ToolArg(description = "The Camel route content (YAML, XML, or Java DSL)") String route,
            @ToolArg(description = "Route format: yaml, xml, or java (default: yaml)") String format) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            String resolvedFormat = format != null && !format.isBlank() ? format.toLowerCase() : "yaml";

            JsonObject result = new JsonObject();
            result.put("format", resolvedFormat);
            result.put("route", route);

            // Analyze security-sensitive components
            List<String> securityComponents = extractSecurityComponents(route);
            JsonArray securityComponentsJson = new JsonArray();
            for (String comp : securityComponents) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    JsonObject compJson = new JsonObject();
                    compJson.put("name", comp);
                    compJson.put("title", model.getTitle());
                    compJson.put("description", model.getDescription());
                    compJson.put("label", model.getLabel());
                    compJson.put("securityConsiderations", securityData.getSecurityConsiderations(comp));
                    compJson.put("riskLevel", securityData.getRiskLevel(comp));
                    securityComponentsJson.add(compJson);
                }
            }
            result.put("securitySensitiveComponents", securityComponentsJson);

            // Security analysis
            JsonObject securityAnalysis = analyzeSecurityConcerns(route);
            result.put("securityAnalysis", securityAnalysis);

            // Best practices
            JsonArray bestPractices = new JsonArray();
            for (String practice : securityData.getBestPractices()) {
                bestPractices.add(practice);
            }
            result.put("securityBestPractices", bestPractices);

            // Summary
            JsonObject summary = new JsonObject();
            summary.put("securityComponentCount", securityComponentsJson.size());
            summary.put("criticalRiskComponents", countComponentsByRisk(securityComponents, "critical"));
            summary.put("highRiskComponents", countComponentsByRisk(securityComponents, "high"));
            summary.put("concernCount", securityAnalysis.getInteger("concernCount"));
            summary.put("positiveCount", securityAnalysis.getInteger("positiveCount"));
            summary.put("hasExternalConnections", hasExternalConnections(route));
            summary.put("hasSecretsManagement", hasSecretsManagement(route));
            summary.put("usesTLS", usesTLS(route));
            summary.put("hasAuthentication", hasAuthentication(route));
            result.put("summary", summary);

            return result.toJson();
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to analyze route security (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Extract security-sensitive components from route content.
     */
    private List<String> extractSecurityComponents(String route) {
        List<String> found = new ArrayList<>();
        String lowerRoute = route.toLowerCase();

        for (String comp : securityData.getSecuritySensitiveComponents()) {
            if (containsComponent(lowerRoute, comp)) {
                found.add(comp);
            }
        }

        return found;
    }

    /**
     * Analyze security concerns in the route.
     */
    private JsonObject analyzeSecurityConcerns(String route) {
        JsonObject analysis = new JsonObject();
        JsonArray concerns = new JsonArray();
        JsonArray positives = new JsonArray();
        String lowerRoute = route.toLowerCase();

        // Check for hardcoded credentials
        if (containsHardcodedCredentials(lowerRoute)) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "critical");
            concern.put("category", "Secrets Management");
            concern.put("issue", "Potential hardcoded credentials detected");
            concern.put("recommendation", "Use property placeholders {{secret}} or vault services for credentials");
            concerns.add(concern);
        }

        // Check for HTTP instead of HTTPS
        if (lowerRoute.contains("http:") && !lowerRoute.contains("https:")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "high");
            concern.put("category", "Encryption");
            concern.put("issue", "Using HTTP instead of HTTPS");
            concern.put("recommendation", "Use HTTPS for secure communication. Configure TLS version 1.2 or higher");
            concerns.add(concern);
        }

        // Check for plain FTP
        if (lowerRoute.contains("ftp:") && !lowerRoute.contains("sftp:") && !lowerRoute.contains("ftps:")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "high");
            concern.put("category", "Encryption");
            concern.put("issue", "Using plain FTP instead of SFTP/FTPS");
            concern.put("recommendation", "Use SFTP or FTPS for encrypted file transfers");
            concerns.add(concern);
        }

        // Check for SSL/TLS disabled
        if (lowerRoute.contains("sslcontextparameters") && lowerRoute.contains("false")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "critical");
            concern.put("category", "Encryption");
            concern.put("issue", "SSL/TLS may be disabled or misconfigured");
            concern.put("recommendation", "Ensure SSL/TLS is properly enabled and configured");
            concerns.add(concern);
        }

        // Check for exec component (high risk)
        if (lowerRoute.contains("exec:")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "critical");
            concern.put("category", "Command Injection");
            concern.put("issue", "Using exec component - high risk for command injection");
            concern.put("recommendation",
                    "Validate all inputs strictly. Consider if exec is really necessary or if safer alternatives exist");
            concerns.add(concern);
        }

        // Check for SQL without parameterized queries indicator
        if (lowerRoute.contains("sql:") && !lowerRoute.contains(":#") && !lowerRoute.contains(":?")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "high");
            concern.put("category", "SQL Injection");
            concern.put("issue", "SQL query may not use parameterized queries");
            concern.put("recommendation", "Use parameterized queries with named parameters (:#param) or positional (:?)");
            concerns.add(concern);
        }

        // Check for LDAP injection risk
        if (lowerRoute.contains("ldap:") && !lowerRoute.contains("ldaps:")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "medium");
            concern.put("category", "Encryption");
            concern.put("issue", "Using LDAP instead of LDAPS");
            concern.put("recommendation", "Use LDAPS for encrypted LDAP communication");
            concerns.add(concern);
        }

        // Check for file component path validation
        if (lowerRoute.contains("file:") && lowerRoute.contains("${")) {
            JsonObject concern = new JsonObject();
            concern.put("severity", "medium");
            concern.put("category", "Path Traversal");
            concern.put("issue", "File path contains dynamic expression - potential path traversal risk");
            concern.put("recommendation", "Validate file paths and restrict to allowed directories");
            concerns.add(concern);
        }

        // POSITIVE CHECKS

        // Check for TLS/SSL usage
        if (usesTLS(lowerRoute)) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Encryption");
            positive.put("finding", "TLS/SSL encryption is configured");
            positives.add(positive);
        }

        // Check for property placeholders (good practice)
        if (lowerRoute.contains("{{") && lowerRoute.contains("}}")) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Secrets Management");
            positive.put("finding", "Using property placeholders for configuration");
            positives.add(positive);
        }

        // Check for vault integration
        if (hasSecretsManagement(lowerRoute)) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Secrets Management");
            positive.put("finding", "Integrated with secrets management service");
            positives.add(positive);
        }

        // Check for authentication configuration
        if (hasAuthentication(lowerRoute)) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Authentication");
            positive.put("finding", "Authentication appears to be configured");
            positives.add(positive);
        }

        // Check for HTTPS usage
        if (lowerRoute.contains("https:")) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Encryption");
            positive.put("finding", "Using HTTPS for secure HTTP communication");
            positives.add(positive);
        }

        // Check for SFTP/FTPS usage
        if (lowerRoute.contains("sftp:") || lowerRoute.contains("ftps:")) {
            JsonObject positive = new JsonObject();
            positive.put("category", "Encryption");
            positive.put("finding", "Using secure file transfer protocol (SFTP/FTPS)");
            positives.add(positive);
        }

        analysis.put("concerns", concerns);
        analysis.put("positives", positives);
        analysis.put("concernCount", concerns.size());
        analysis.put("positiveCount", positives.size());

        return analysis;
    }

    private int countComponentsByRisk(List<String> components, String riskLevel) {
        return (int) components.stream()
                .filter(c -> riskLevel.equals(securityData.getRiskLevel(c)))
                .count();
    }

    private boolean containsHardcodedCredentials(String route) {
        return (route.contains("password=") || route.contains("password:"))
                && !route.contains("{{") && !route.contains("$");
    }

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
    }

    private boolean hasExternalConnections(String route) {
        String lowerRoute = route.toLowerCase();
        return lowerRoute.contains("http:") || lowerRoute.contains("https:")
                || lowerRoute.contains("kafka:") || lowerRoute.contains("jms:")
                || lowerRoute.contains("sql:") || lowerRoute.contains("mongodb:")
                || lowerRoute.contains("aws2-") || lowerRoute.contains("azure-");
    }

    private boolean hasSecretsManagement(String route) {
        String lowerRoute = route.toLowerCase();
        return lowerRoute.contains("hashicorp-vault") || lowerRoute.contains("aws-secrets-manager")
                || lowerRoute.contains("aws2-secrets-manager")
                || lowerRoute.contains("azure-key-vault") || lowerRoute.contains("google-secret-manager")
                || lowerRoute.contains("cyberark-vault");
    }

    private boolean usesTLS(String route) {
        String lowerRoute = route.toLowerCase();
        return lowerRoute.contains("ssl=true") || lowerRoute.contains("usessl=true")
                || lowerRoute.contains("https:") || lowerRoute.contains("sftp:")
                || lowerRoute.contains("ftps:") || lowerRoute.contains("ldaps:")
                || lowerRoute.contains("smtps:") || lowerRoute.contains("imaps:")
                || lowerRoute.contains("sslcontextparameters");
    }

    private boolean hasAuthentication(String route) {
        String lowerRoute = route.toLowerCase();
        return lowerRoute.contains("username=") || lowerRoute.contains("authmethod=")
                || lowerRoute.contains("saslmechanism=") || lowerRoute.contains("oauth")
                || lowerRoute.contains("bearer") || lowerRoute.contains("apikey")
                || lowerRoute.contains("securityprovider");
    }
}
