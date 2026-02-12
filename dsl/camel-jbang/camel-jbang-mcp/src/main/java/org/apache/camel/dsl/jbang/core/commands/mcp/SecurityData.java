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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shared holder for security reference data used by both {@link HardenTools} (MCP Tool) and {@link SecurityResources}
 * (MCP Resources).
 * <p>
 * Contains the registry of security-sensitive Camel components, best practices, per-component security considerations,
 * and risk levels.
 */
@ApplicationScoped
public class SecurityData {

    private static final List<String> SECURITY_SENSITIVE_COMPONENTS = Arrays.asList(
            // Network/API components - need TLS, authentication
            "http", "https", "netty-http", "vertx-http", "websocket",
            "rest", "rest-api", "platform-http", "servlet", "undertow", "jetty",
            // Messaging - need authentication, encryption
            "kafka", "jms", "activemq", "amqp", "rabbitmq", "pulsar",
            "aws2-sqs", "aws2-sns", "aws2-kinesis",
            "azure-servicebus", "azure-eventhubs",
            "google-pubsub",
            // File/Storage - need access control, path validation
            "file", "ftp", "sftp", "ftps",
            "aws2-s3", "azure-storage-blob", "azure-storage-queue", "azure-files",
            "google-storage", "minio",
            // Database - need authentication, SQL injection prevention
            "sql", "jdbc", "mongodb", "couchdb", "cassandraql",
            "elasticsearch", "opensearch", "redis",
            // Email - need authentication, TLS
            "smtp", "smtps", "imap", "imaps", "pop3", "pop3s",
            // Remote execution - high risk, need strict validation
            "exec", "ssh", "docker",
            // Directory services - need secure binding
            "ldap", "ldaps",
            // Secrets management
            "hashicorp-vault", "aws2-secrets-manager", "azure-key-vault", "google-secret-manager");

    private static final List<String> SECURITY_BEST_PRACTICES = Arrays.asList(
            "Use TLS/SSL (version 1.2+) for all network communications",
            "Store secrets in vault services (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, etc.)",
            "Use property placeholders for sensitive configuration values",
            "Enable authentication for all endpoints and services",
            "Validate and sanitize all input data to prevent injection attacks",
            "Use parameterized queries for database operations",
            "Implement proper certificate validation - do not disable SSL verification",
            "Use principle of least privilege for service accounts and IAM roles",
            "Enable audit logging for sensitive operations",
            "Implement proper error handling without exposing internal details",
            "Use HTTPS instead of HTTP for all external communications",
            "Configure appropriate timeouts to prevent resource exhaustion",
            "Validate file paths to prevent path traversal attacks",
            "Use SFTP/FTPS instead of plain FTP");

    private static final Map<String, List<String>> COMPONENTS_BY_CATEGORY;

    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Network/API", Arrays.asList(
                "http", "https", "netty-http", "vertx-http", "websocket",
                "rest", "rest-api", "platform-http", "servlet", "undertow", "jetty"));
        map.put("Messaging", Arrays.asList(
                "kafka", "jms", "activemq", "amqp", "rabbitmq", "pulsar",
                "aws2-sqs", "aws2-sns", "aws2-kinesis",
                "azure-servicebus", "azure-eventhubs",
                "google-pubsub"));
        map.put("File/Storage", Arrays.asList(
                "file", "ftp", "sftp", "ftps",
                "aws2-s3", "azure-storage-blob", "azure-storage-queue", "azure-files",
                "google-storage", "minio"));
        map.put("Database", Arrays.asList(
                "sql", "jdbc", "mongodb", "couchdb", "cassandraql",
                "elasticsearch", "opensearch", "redis"));
        map.put("Email", Arrays.asList(
                "smtp", "smtps", "imap", "imaps", "pop3", "pop3s"));
        map.put("Remote Execution", Arrays.asList(
                "exec", "ssh", "docker"));
        map.put("Directory Services", Arrays.asList(
                "ldap", "ldaps"));
        map.put("Secrets Management", Arrays.asList(
                "hashicorp-vault", "aws2-secrets-manager", "azure-key-vault", "google-secret-manager"));
        COMPONENTS_BY_CATEGORY = Collections.unmodifiableMap(map);
    }

    public List<String> getSecuritySensitiveComponents() {
        return SECURITY_SENSITIVE_COMPONENTS;
    }

    public List<String> getBestPractices() {
        return SECURITY_BEST_PRACTICES;
    }

    public Map<String, List<String>> getComponentsByCategory() {
        return COMPONENTS_BY_CATEGORY;
    }

    /**
     * Get the category for a given component name.
     */
    public String getCategory(String component) {
        for (Map.Entry<String, List<String>> entry : COMPONENTS_BY_CATEGORY.entrySet()) {
            if (entry.getValue().contains(component)) {
                return entry.getKey();
            }
        }
        return "Other";
    }

    /**
     * Get security considerations for a specific component.
     */
    public String getSecurityConsiderations(String component) {
        return switch (component) {
            case "http" ->
                "Prefer HTTPS over HTTP. Validate certificates. Configure appropriate timeouts. Set security headers.";
            case "https" ->
                "Verify TLS version is 1.2 or higher. Enable certificate validation. Configure secure cipher suites.";
            case "kafka" ->
                "Enable SASL authentication (SCRAM-SHA-256/512 or GSSAPI). Use SSL for encryption. Configure ACLs for authorization.";
            case "sql", "jdbc" ->
                "Use parameterized queries to prevent SQL injection. Limit database user privileges. Enable connection encryption.";
            case "file" ->
                "Validate file paths to prevent traversal attacks. Restrict directory access. Set appropriate file permissions.";
            case "ftp" ->
                "INSECURE: Use SFTP or FTPS instead. Plain FTP transmits credentials in cleartext.";
            case "sftp" ->
                "Use key-based authentication. Validate host keys. Configure known_hosts file.";
            case "ftps" ->
                "Enable explicit FTPS. Verify server certificates. Use strong TLS version.";
            case "exec" ->
                "HIGH RISK: Validate and sanitize all inputs to prevent command injection. Consider safer alternatives.";
            case "ssh" ->
                "Use key-based authentication. Validate host keys. Disable password authentication if possible.";
            case "rest", "rest-api", "platform-http" ->
                "Implement authentication (OAuth2, JWT, API keys). Validate all input. Set CORS policies. Add security headers.";
            case "ldap" ->
                "Use LDAPS for encryption. Escape special characters to prevent LDAP injection. Use service account with minimal privileges.";
            case "ldaps" ->
                "Verify server certificates. Use strong TLS. Escape special characters in queries.";
            case "mongodb" ->
                "Enable authentication. Use TLS for connections. Limit network exposure. Use SCRAM authentication.";
            case "redis" ->
                "Enable authentication (requirepass or ACL). Use TLS. Limit network exposure. Disable dangerous commands.";
            case "jms", "activemq", "amqp", "rabbitmq" ->
                "Enable authentication. Use SSL/TLS for connections. Configure authorization policies.";
            case "aws2-s3", "aws2-sqs", "aws2-sns", "aws2-kinesis" ->
                "Use IAM roles instead of access keys. Enable server-side encryption. Configure bucket/queue policies.";
            case "aws2-secrets-manager" ->
                "Use IAM roles for access. Enable automatic rotation. Audit secret access.";
            case "azure-storage-blob", "azure-storage-queue", "azure-files" ->
                "Use managed identities. Enable encryption at rest. Configure access policies.";
            case "azure-key-vault" ->
                "Use managed identities. Enable soft-delete. Configure access policies and RBAC.";
            case "google-storage", "google-pubsub" ->
                "Use service accounts with minimal permissions. Enable encryption. Configure IAM policies.";
            case "google-secret-manager" ->
                "Use service accounts. Enable automatic rotation. Audit access.";
            case "hashicorp-vault" ->
                "Use AppRole or Kubernetes auth. Configure token TTLs. Enable audit logging.";
            case "elasticsearch", "opensearch" ->
                "Enable authentication. Use TLS. Configure role-based access control.";
            case "smtp", "smtps", "imap", "imaps", "pop3", "pop3s" ->
                "Use TLS variants (SMTPS, IMAPS, POP3S). Use secure authentication. Store credentials securely.";
            case "websocket" ->
                "Use WSS (WebSocket Secure). Implement authentication. Validate origin headers.";
            case "docker" ->
                "HIGH RISK: Validate all inputs. Use least privilege. Consider container security policies.";
            case "netty-http", "vertx-http", "undertow", "jetty", "servlet" ->
                "Enable TLS. Implement authentication. Set security headers. Validate input.";
            case "pulsar" ->
                "Enable TLS encryption. Configure authentication (JWT, Athenz). Set authorization policies.";
            case "minio" ->
                "Enable TLS. Use access/secret keys securely. Configure bucket policies.";
            case "couchdb", "cassandraql" ->
                "Enable authentication. Use TLS for connections. Configure role-based access.";
            default -> "Review security configuration for this component";
        };
    }

    /**
     * Get risk level for a component.
     */
    public String getRiskLevel(String component) {
        return switch (component) {
            case "exec", "docker" -> "critical";
            case "http", "ftp", "ldap", "sql", "jdbc" -> "high";
            case "file", "ssh", "rest", "rest-api", "platform-http", "kafka", "mongodb", "redis" -> "medium";
            default -> "low";
        };
    }
}
