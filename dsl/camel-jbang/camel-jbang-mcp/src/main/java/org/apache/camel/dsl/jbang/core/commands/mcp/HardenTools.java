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
import org.apache.camel.tooling.model.ComponentModel;

/**
 * MCP Tool for providing security hardening context and analysis for Camel routes.
 * <p>
 * This tool analyzes routes for security-sensitive components, identifies potential vulnerabilities, and provides
 * structured context that an LLM can use to formulate security hardening recommendations.
 */
@ApplicationScoped
public class HardenTools {

    @Inject
    CatalogService catalogService;

    @Inject
    SecurityData securityData;

    /**
     * Tool to get security hardening context for a Camel route.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get security hardening analysis context for a Camel route. " +
                        "Returns security-sensitive components, potential vulnerabilities, " +
                        "and security best practices. Use this context to provide security " +
                        "hardening recommendations for the route.")
    public HardenContextResult camel_route_harden_context(
            @ToolArg(description = "The Camel route content (YAML, XML, or Java DSL)") String route,
            @ToolArg(description = "Route format: yaml, xml, or java (default: yaml)") String format,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to use (e.g., 4.17.0). If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            String resolvedFormat = format != null && !format.isBlank() ? format.toLowerCase() : "yaml";

            // Analyze security-sensitive components
            List<String> securityComponentNames = extractSecurityComponents(route);
            List<SecurityComponent> securityComponents = new ArrayList<>();
            for (String comp : securityComponentNames) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    securityComponents.add(new SecurityComponent(
                            comp, model.getTitle(), model.getDescription(), model.getLabel(),
                            securityData.getSecurityConsiderations(comp), securityData.getRiskLevel(comp)));
                }
            }

            // Security analysis
            SecurityAnalysis securityAnalysis = analyzeSecurityConcerns(route);

            // Best practices
            List<String> bestPractices = List.copyOf(securityData.getBestPractices());

            // Summary
            HardenSummary summary = new HardenSummary(
                    securityComponents.size(),
                    countComponentsByRisk(securityComponentNames, "critical"),
                    countComponentsByRisk(securityComponentNames, "high"),
                    securityAnalysis.concernCount(), securityAnalysis.positiveCount(),
                    hasExternalConnections(route), hasSecretsManagement(route),
                    usesTLS(route), hasAuthentication(route));

            return new HardenContextResult(
                    resolvedFormat, route, securityComponents, securityAnalysis, bestPractices, summary);
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
    private SecurityAnalysis analyzeSecurityConcerns(String route) {
        List<SecurityConcern> concerns = new ArrayList<>();
        List<SecurityPositive> positives = new ArrayList<>();
        String lowerRoute = route.toLowerCase();

        // Check for hardcoded credentials
        if (containsHardcodedCredentials(lowerRoute)) {
            concerns.add(new SecurityConcern(
                    "critical", "Secrets Management",
                    "Potential hardcoded credentials detected",
                    "Use property placeholders {{secret}} or vault services for credentials"));
        }

        // Check for HTTP instead of HTTPS
        if (lowerRoute.contains("http:") && !lowerRoute.contains("https:")) {
            concerns.add(new SecurityConcern(
                    "high", "Encryption",
                    "Using HTTP instead of HTTPS",
                    "Use HTTPS for secure communication. Configure TLS version 1.2 or higher"));
        }

        // Check for plain FTP
        if (lowerRoute.contains("ftp:") && !lowerRoute.contains("sftp:") && !lowerRoute.contains("ftps:")) {
            concerns.add(new SecurityConcern(
                    "high", "Encryption",
                    "Using plain FTP instead of SFTP/FTPS",
                    "Use SFTP or FTPS for encrypted file transfers"));
        }

        // Check for SSL/TLS disabled
        if (lowerRoute.contains("sslcontextparameters") && lowerRoute.contains("false")) {
            concerns.add(new SecurityConcern(
                    "critical", "Encryption",
                    "SSL/TLS may be disabled or misconfigured",
                    "Ensure SSL/TLS is properly enabled and configured"));
        }

        // Check for exec component (high risk)
        if (lowerRoute.contains("exec:")) {
            concerns.add(new SecurityConcern(
                    "critical", "Command Injection",
                    "Using exec component - high risk for command injection",
                    "Validate all inputs strictly. Consider if exec is really necessary or if safer alternatives exist"));
        }

        // Check for SQL without parameterized queries indicator
        if (lowerRoute.contains("sql:") && !lowerRoute.contains(":#") && !lowerRoute.contains(":?")) {
            concerns.add(new SecurityConcern(
                    "high", "SQL Injection",
                    "SQL query may not use parameterized queries",
                    "Use parameterized queries with named parameters (:#param) or positional (:?)"));
        }

        // Check for LDAP injection risk
        if (lowerRoute.contains("ldap:") && !lowerRoute.contains("ldaps:")) {
            concerns.add(new SecurityConcern(
                    "medium", "Encryption",
                    "Using LDAP instead of LDAPS",
                    "Use LDAPS for encrypted LDAP communication"));
        }

        // Check for file component path validation
        if (lowerRoute.contains("file:") && lowerRoute.contains("${")) {
            concerns.add(new SecurityConcern(
                    "medium", "Path Traversal",
                    "File path contains dynamic expression - potential path traversal risk",
                    "Validate file paths and restrict to allowed directories"));
        }

        // POSITIVE CHECKS

        // Check for TLS/SSL usage
        if (usesTLS(lowerRoute)) {
            positives.add(new SecurityPositive("Encryption", "TLS/SSL encryption is configured"));
        }

        // Check for property placeholders (good practice)
        if (lowerRoute.contains("{{") && lowerRoute.contains("}}")) {
            positives.add(new SecurityPositive("Secrets Management", "Using property placeholders for configuration"));
        }

        // Check for vault integration
        if (hasSecretsManagement(lowerRoute)) {
            positives.add(new SecurityPositive("Secrets Management", "Integrated with secrets management service"));
        }

        // Check for authentication configuration
        if (hasAuthentication(lowerRoute)) {
            positives.add(new SecurityPositive("Authentication", "Authentication appears to be configured"));
        }

        // Check for HTTPS usage
        if (lowerRoute.contains("https:")) {
            positives.add(new SecurityPositive("Encryption", "Using HTTPS for secure HTTP communication"));
        }

        // Check for SFTP/FTPS usage
        if (lowerRoute.contains("sftp:") || lowerRoute.contains("ftps:")) {
            positives.add(new SecurityPositive("Encryption", "Using secure file transfer protocol (SFTP/FTPS)"));
        }

        return new SecurityAnalysis(concerns, positives, concerns.size(), positives.size());
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

    // Result records

    public record HardenContextResult(
            String format, String route, List<SecurityComponent> securitySensitiveComponents,
            SecurityAnalysis securityAnalysis, List<String> securityBestPractices,
            HardenSummary summary) {
    }

    public record SecurityComponent(
            String name, String title, String description, String label,
            String securityConsiderations, String riskLevel) {
    }

    public record SecurityAnalysis(
            List<SecurityConcern> concerns, List<SecurityPositive> positives,
            int concernCount, int positiveCount) {
    }

    public record SecurityConcern(String severity, String category, String issue, String recommendation) {
    }

    public record SecurityPositive(String category, String finding) {
    }

    public record HardenSummary(
            int securityComponentCount, int criticalRiskComponents, int highRiskComponents,
            int concernCount, int positiveCount, boolean hasExternalConnections,
            boolean hasSecretsManagement, boolean usesTLS, boolean hasAuthentication) {
    }
}
