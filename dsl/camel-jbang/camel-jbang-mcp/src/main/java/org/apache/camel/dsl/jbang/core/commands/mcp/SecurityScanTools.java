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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.SecurityUtils;

/**
 * MCP Tool for static security analysis of Camel route definitions.
 * <p>
 * Scans route content (YAML, XML, or Java DSL) for security anti-patterns defined in the Camel security model
 * ({@code design/security.adoc}). Produces actionable findings with severity, line location, and remediation guidance.
 * <p>
 * Distinct from {@link HardenTools} which provides general security context and CVE advisories — this tool performs
 * line-by-line static analysis to find specific violations.
 */
@ApplicationScoped
public class SecurityScanTools {

    private static final Pattern SECRET_IN_URI = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|apikey|api[_-]?key|access[_-]?key)\\s*[=:]\\s*(?!\\{\\{)(?!\\$\\{)([^\\s,;}'\"\\]&]+)");

    private static final Pattern CONNECTION_STRING_CREDS = Pattern.compile(
            "(?i)(mongodb(\\+srv)?://|amqp://|redis://|jdbc:)\\S+:\\S+@");

    private static final String[] CONSUMER_COMPONENTS = {
            "platform-http", "netty-http", "jetty", "undertow", "servlet",
            "cxf", "cxfrs", "rest", "coap", "grpc"
    };

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Scan a Camel route for security anti-patterns. "
                        + "Performs static analysis to detect: exposed secrets in URIs, "
                        + "insecure configuration options (trustAllCertificates, allowJavaSerializedObject, etc.), "
                        + "missing Camel* header filters on consumers, unencrypted protocols, "
                        + "and other violations from the Camel security model. "
                        + "Returns findings with severity, line number, and remediation guidance. "
                        + "Distinct from camel_route_harden_context which provides general security context "
                        + "and CVE advisories.")
    public SecurityScanResult camel_security_scan(
            @ToolArg(description = "The Camel route content (YAML, XML, or Java DSL)") String route,
            @ToolArg(description = "Route format: yaml, xml, or java (default: yaml)") String format) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            String resolvedFormat = format != null && !format.isBlank() ? format.toLowerCase(Locale.ROOT) : "yaml";
            String[] lines = route.split("\n", -1);

            List<Finding> findings = new ArrayList<>();

            scanInsecureOptions(lines, findings);
            scanSecretsInUris(lines, findings);
            scanConnectionStringCredentials(lines, findings);
            scanUnencryptedProtocols(lines, findings);
            scanMissingHeaderFilters(route, lines, findings);
            scanExecComponent(lines, findings);
            scanSqlInjection(lines, findings);
            scanFilePathTraversal(lines, findings);

            findings.sort((a, b) -> severityRank(a.severity()) - severityRank(b.severity()));

            int critical = (int) findings.stream().filter(f -> "critical".equals(f.severity())).count();
            int high = (int) findings.stream().filter(f -> "high".equals(f.severity())).count();
            int medium = (int) findings.stream().filter(f -> "medium".equals(f.severity())).count();
            int low = (int) findings.stream().filter(f -> "low".equals(f.severity())).count();

            return new SecurityScanResult(
                    resolvedFormat, findings, findings.size(),
                    new SeverityCounts(critical, high, medium, low));
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to scan route (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    private void scanInsecureOptions(String[] lines, List<Finding> findings) {
        Map<String, SecurityUtils.SecurityOption> securityOptions = SecurityUtils.getSecurityOptions();

        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT).replaceAll("[\\s-]", "");
            for (Map.Entry<String, SecurityUtils.SecurityOption> entry : securityOptions.entrySet()) {
                String optionKey = entry.getKey();
                String insecureValue = entry.getValue().insecureValue();
                String category = entry.getValue().category();

                if (insecureValue == null || insecureValue.isEmpty()) {
                    continue;
                }
                if (lower.contains(optionKey + "=" + insecureValue)
                        || lower.contains(optionKey + ":" + insecureValue)
                        || lower.contains(optionKey + "\":" + insecureValue)
                        || lower.contains("\"" + optionKey + "\":" + insecureValue)) {
                    findings.add(new Finding(
                            severityForCategory(category),
                            category,
                            "Insecure option: " + optionKey + "=" + insecureValue,
                            i + 1,
                            remediationForCategory(category, optionKey)));
                }
            }
        }
    }

    private void scanSecretsInUris(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = SECRET_IN_URI.matcher(lines[i]);
            while (matcher.find()) {
                String key = matcher.group(1);
                findings.add(new Finding(
                        "critical",
                        "secret",
                        "Potential plain-text secret in URI: " + key,
                        i + 1,
                        "Use property placeholders {{" + key + "}} or a secrets management vault "
                               + "(HashiCorp Vault, AWS Secrets Manager, Azure Key Vault)"));
            }
        }
    }

    private void scanConnectionStringCredentials(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            if (CONNECTION_STRING_CREDS.matcher(lines[i]).find()) {
                findings.add(new Finding(
                        "critical",
                        "secret",
                        "Connection string with embedded credentials",
                        i + 1,
                        "Extract credentials from the connection string and use property placeholders or vault services"));
            }
        }
    }

    private void scanUnencryptedProtocols(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);

            if (containsScheme(lower, "http") && !containsScheme(lower, "https")
                    && !lower.contains("platform-http")) {
                findings.add(new Finding(
                        "high",
                        "insecure:ssl",
                        "Using HTTP instead of HTTPS",
                        i + 1,
                        "Use HTTPS with TLS 1.2+ for secure communication"));
            }
            if (containsScheme(lower, "ftp") && !containsScheme(lower, "sftp") && !containsScheme(lower, "ftps")) {
                findings.add(new Finding(
                        "high",
                        "insecure:ssl",
                        "Using plain FTP instead of SFTP/FTPS",
                        i + 1,
                        "Use SFTP or FTPS for encrypted file transfers"));
            }
            if (containsScheme(lower, "ldap") && !containsScheme(lower, "ldaps")) {
                findings.add(new Finding(
                        "medium",
                        "insecure:ssl",
                        "Using LDAP instead of LDAPS",
                        i + 1,
                        "Use LDAPS for encrypted LDAP communication"));
            }
            if (containsScheme(lower, "smtp") && !containsScheme(lower, "smtps")) {
                findings.add(new Finding(
                        "medium",
                        "insecure:ssl",
                        "Using SMTP instead of SMTPS",
                        i + 1,
                        "Use SMTPS for encrypted email communication"));
            }
        }
    }

    private void scanMissingHeaderFilters(String route, String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);
            for (String consumer : CONSUMER_COMPONENTS) {
                if (containsScheme(lower, consumer)) {
                    if (!hasHeaderFilterNearby(lines, i)) {
                        findings.add(new Finding(
                                "high",
                                "header-injection",
                                "Consumer '" + consumer + "' without Camel* header filter",
                                i + 1,
                                "Add removeHeaders(\"Camel*\") or a HeaderFilterStrategy to prevent "
                                       + "external clients from injecting Camel-internal headers "
                                       + "(CVE-2025-27636 and related)"));
                    }
                }
            }
        }
    }

    private boolean hasHeaderFilterNearby(String[] lines, int consumerLine) {
        int searchEnd = Math.min(lines.length, consumerLine + 20);
        for (int i = consumerLine; i < searchEnd; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);
            if (lower.contains("removeheaders") || lower.contains("headerfilter")
                    || lower.contains("\"camel*\"") || lower.contains("'camel*'")) {
                return true;
            }
            if (i > consumerLine && containsAnyConsumerScheme(lower)) {
                break;
            }
        }
        return false;
    }

    private boolean containsAnyConsumerScheme(String lower) {
        for (String consumer : CONSUMER_COMPONENTS) {
            if (containsScheme(lower, consumer)) {
                return true;
            }
        }
        return false;
    }

    private void scanExecComponent(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            if (containsScheme(lines[i].toLowerCase(Locale.ROOT), "exec")) {
                findings.add(new Finding(
                        "critical",
                        "command-injection",
                        "Using exec component — high risk for command injection",
                        i + 1,
                        "Validate all inputs strictly. Consider safer alternatives. "
                               + "Never pass untrusted input directly to exec"));
            }
        }
    }

    private void scanSqlInjection(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);
            if (containsScheme(lower, "sql") && !lower.contains(":#") && !lower.contains(":?")) {
                findings.add(new Finding(
                        "high",
                        "sql-injection",
                        "SQL query may not use parameterized queries",
                        i + 1,
                        "Use parameterized queries with named parameters (:#param) or positional (:?)"));
            }
        }
    }

    private void scanFilePathTraversal(String[] lines, List<Finding> findings) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);
            if (containsScheme(lower, "file") && (lower.contains("${") || lower.contains("$simple{"))) {
                findings.add(new Finding(
                        "medium",
                        "path-traversal",
                        "File path contains dynamic expression — potential path traversal risk",
                        i + 1,
                        "Validate file paths and restrict to allowed directories using fileName option"));
            }
        }
    }

    private static boolean containsScheme(String text, String scheme) {
        int idx = text.indexOf(scheme + ":");
        if (idx >= 0) {
            if (idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1)) && text.charAt(idx - 1) != '-') {
                return true;
            }
        }
        return text.contains("\"" + scheme + "\"") || text.contains("'" + scheme + "'");
    }

    private static int findLineContaining(String[] lines, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase(Locale.ROOT).contains(lower)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String severityForCategory(String category) {
        return switch (category) {
            case "insecure:serialization" -> "critical";
            case "insecure:ssl" -> "high";
            case "insecure:dev" -> "medium";
            case "secret" -> "critical";
            default -> "medium";
        };
    }

    private static String remediationForCategory(String category, String optionKey) {
        return switch (category) {
            case "insecure:ssl" -> "Remove " + optionKey + " or set it to a secure value. "
                                   + "Configure TLS via SSLContextParameters with proper certificate validation";
            case "insecure:serialization" -> "Remove " + optionKey + " or set it to a secure value. "
                                             + "Java serialization of untrusted input enables remote code execution";
            case "insecure:dev" -> "Disable " + optionKey + " in production. "
                                   + "Development features can expose internal state or enable remote control";
            case "secret" -> "Use property placeholders or vault services instead of plain-text secrets";
            default -> "Review and correct the insecure configuration";
        };
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "critical" -> 0;
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 4;
        };
    }

    // Result records

    public record SecurityScanResult(
            String format, List<Finding> findings, int totalFindings,
            SeverityCounts severityCounts) {
    }

    public record Finding(
            String severity, String category, String issue,
            int line, String remediation) {
    }

    public record SeverityCounts(int critical, int high, int medium, int low) {
    }
}
