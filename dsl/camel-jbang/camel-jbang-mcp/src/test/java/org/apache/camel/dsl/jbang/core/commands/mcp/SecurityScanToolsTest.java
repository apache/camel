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

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityScanToolsTest {

    private final SecurityScanTools tools = new SecurityScanTools();

    @Test
    void nullRouteThrowsException() {
        assertThatThrownBy(() -> tools.camel_security_scan(null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void blankRouteThrowsException() {
        assertThatThrownBy(() -> tools.camel_security_scan("  ", null))
                .isInstanceOf(ToolCallException.class);
    }

    @Test
    void cleanRouteProducesNoFindings() {
        String route = """
                - route:
                    from:
                      uri: timer:tick
                    steps:
                      - log: "Hello"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.totalFindings()).isZero();
        assertThat(result.format()).isEqualTo("yaml");
    }

    @Test
    void detectsTrustAllCertificates() {
        String route = """
                - route:
                    from:
                      uri: https://api.example.com?trustAllCertificates=true
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("trustallcertificates")
                && f.category().equals("insecure:ssl"));
    }

    @Test
    void detectsAllowJavaSerializedObject() {
        String route = """
                - route:
                    from:
                      uri: jms:queue:orders?allowJavaSerializedObject=true
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("allowjavaserializedobject")
                && f.category().equals("insecure:serialization")
                && f.severity().equals("critical"));
    }

    @Test
    void detectsTransferExceptionEnabled() {
        String route = """
                - route:
                    from:
                      uri: netty-http:0.0.0.0:8080?transferException=true
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("transferexception"));
    }

    @Test
    void detectsPlainTextPasswordInUri() {
        String route = """
                - route:
                    from:
                      uri: kafka:topic?password=mysecret123&brokers=localhost
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("secret")
                && f.severity().equals("critical")
                && f.issue().contains("password"));
    }

    @Test
    void doesNotFlagPlaceholderPassword() {
        String route = """
                - route:
                    from:
                      uri: kafka:topic?password={{kafka.password}}&brokers=localhost
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).noneMatch(f -> f.category().equals("secret")
                && f.issue().contains("password"));
    }

    @Test
    void detectsConnectionStringWithCredentials() {
        String route = """
                - route:
                    from:
                      uri: mongodb:myDb?connectionBean=#mongoClient
                    steps:
                      - setBody:
                          constant: "mongodb://admin:secret@host:27017/db"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("Connection string with embedded credentials"));
    }

    @Test
    void detectsHttpInsteadOfHttps() {
        String route = """
                - route:
                    from:
                      uri: http://api.example.com/data
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("HTTP instead of HTTPS")
                && f.severity().equals("high"));
    }

    @Test
    void doesNotFlagHttpsAsInsecure() {
        String route = """
                - route:
                    from:
                      uri: https://api.example.com/data
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).noneMatch(f -> f.issue().contains("HTTP instead of HTTPS"));
    }

    @Test
    void detectsPlainFtp() {
        String route = """
                - route:
                    from:
                      uri: ftp://server/files
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("plain FTP"));
    }

    @Test
    void doesNotFlagSftp() {
        String route = """
                - route:
                    from:
                      uri: sftp://server/files
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).noneMatch(f -> f.issue().contains("plain FTP"));
    }

    @Test
    void detectsMissingHeaderFilterOnConsumer() {
        String route = """
                - route:
                    from:
                      uri: platform-http:/api/data
                    steps:
                      - log: "received"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("header-injection")
                && f.issue().contains("platform-http"));
    }

    @Test
    void headerFilterPresentSuppressesFinding() {
        String route = """
                - route:
                    from:
                      uri: platform-http:/api/data
                    steps:
                      - removeHeaders: "Camel*"
                      - log: "received"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).noneMatch(f -> f.category().equals("header-injection"));
    }

    @Test
    void detectsExecComponent() {
        String route = """
                - route:
                    from:
                      uri: direct:run
                    steps:
                      - to: exec:ls
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("command-injection")
                && f.severity().equals("critical"));
    }

    @Test
    void detectsSqlWithoutParameterizedQueries() {
        String route = """
                - route:
                    from:
                      uri: "sql:SELECT * FROM users WHERE name = ${body}"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("sql-injection"));
    }

    @Test
    void detectsFilePathTraversal() {
        String route = """
                - route:
                    from:
                      uri: "file:/data/${header.dir}"
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("path-traversal"));
    }

    @Test
    void findingsAreSortedBySeverity() {
        String route = """
                - route:
                    from:
                      uri: http://api.example.com?password=secret&allowJavaSerializedObject=true
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).isNotEmpty();
        for (int i = 1; i < result.findings().size(); i++) {
            String prev = result.findings().get(i - 1).severity();
            String curr = result.findings().get(i).severity();
            assertThat(severityRank(prev))
                    .as("findings should be sorted by severity: %s before %s", prev, curr)
                    .isLessThanOrEqualTo(severityRank(curr));
        }
    }

    @Test
    void severityCountsAreAccurate() {
        String route = """
                - route:
                    from:
                      uri: http://api.example.com?password=secret&allowJavaSerializedObject=true
                    steps:
                      - to: exec:cmd
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.severityCounts().critical()).isGreaterThan(0);
        assertThat(result.totalFindings())
                .isEqualTo(result.severityCounts().critical() + result.severityCounts().high()
                           + result.severityCounts().medium() + result.severityCounts().low());
    }

    @Test
    void defaultFormatIsYaml() {
        String route = "from: timer:tick";
        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, null);
        assertThat(result.format()).isEqualTo("yaml");
    }

    @Test
    void detectsUnencryptedSmtp() {
        String route = """
                - route:
                    from:
                      uri: smtp://mail.example.com
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.issue().contains("SMTP instead of SMTPS"));
    }

    @Test
    void detectsDevConsoleEnabled() {
        String route = """
                - route:
                    from:
                      uri: timer:tick?devConsoleEnabled=true
                """;

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings()).anyMatch(f -> f.category().equals("insecure:dev"));
    }

    @Test
    void lineNumbersAreCorrect() {
        String route = "line1\nline2\npassword=mysecret\nline4";

        SecurityScanTools.SecurityScanResult result = tools.camel_security_scan(route, "yaml");

        assertThat(result.findings())
                .filteredOn(f -> f.category().equals("secret"))
                .allMatch(f -> f.line() == 3);
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
}
