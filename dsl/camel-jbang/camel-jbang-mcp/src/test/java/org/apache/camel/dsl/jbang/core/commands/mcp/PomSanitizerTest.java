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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PomSanitizerTest {

    // A clean POM with no sensitive data
    private static final String CLEAN_POM = """
            <project>
                <properties>
                    <camel.version>4.10.0</camel.version>
                    <maven.compiler.release>21</maven.compiler.release>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    // A POM with various sensitive elements
    private static final String POM_WITH_CREDENTIALS = """
            <project>
                <properties>
                    <camel.version>4.10.0</camel.version>
                    <db.password>superSecret123</db.password>
                    <api.token>tok_abc123xyz</api.token>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    // A POM with property placeholders (not actual secrets)
    private static final String POM_WITH_PLACEHOLDERS = """
            <project>
                <properties>
                    <camel.version>4.10.0</camel.version>
                    <db.password>${env.DB_PASSWORD}</db.password>
                    <api.token>${env.API_TOKEN}</api.token>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    // A POM with multiple sensitive patterns
    private static final String POM_WITH_MULTIPLE_SENSITIVE = """
            <project>
                <properties>
                    <camel.version>4.10.0</camel.version>
                    <secret>myAppSecret</secret>
                    <apiKey>key_12345</apiKey>
                    <accessKey>AKIA1234567890</accessKey>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    // ---- Detection tests ----

    @Test
    void detectsPasswordElement() {
        List<String> findings = PomSanitizer.detectSensitiveContent(POM_WITH_CREDENTIALS);
        assertThat(findings).anyMatch(f -> f.contains("password"));
    }

    @Test
    void detectsTokenElement() {
        List<String> findings = PomSanitizer.detectSensitiveContent(POM_WITH_CREDENTIALS);
        assertThat(findings).anyMatch(f -> f.contains("token"));
    }

    @Test
    void detectsApiKeyElement() {
        String pom = "<project><properties><apiKey>key123</apiKey></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("apiKey"));
    }

    @Test
    void detectsSecretElement() {
        String pom = "<project><properties><secret>s3cr3t</secret></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("secret"));
    }

    @Test
    void detectsPropertyStyleNames() {
        List<String> findings = PomSanitizer.detectSensitiveContent(POM_WITH_CREDENTIALS);
        assertThat(findings).anyMatch(f -> f.equals("db.password"));
        assertThat(findings).anyMatch(f -> f.equals("api.token"));
    }

    @Test
    void ignoresPropertyPlaceholders() {
        List<String> findings = PomSanitizer.detectSensitiveContent(POM_WITH_PLACEHOLDERS);
        // Property placeholders should not be flagged as sensitive
        assertThat(findings).noneMatch(f -> f.equals("db.password"));
        assertThat(findings).noneMatch(f -> f.equals("api.token"));
    }

    @Test
    void noDetectionForCleanPom() {
        List<String> findings = PomSanitizer.detectSensitiveContent(CLEAN_POM);
        assertThat(findings).isEmpty();
    }

    @Test
    void detectsMultipleSensitiveElements() {
        List<String> findings = PomSanitizer.detectSensitiveContent(POM_WITH_MULTIPLE_SENSITIVE);
        assertThat(findings.size()).isGreaterThanOrEqualTo(3);
    }

    // ---- Sanitization tests ----

    @Test
    void masksPasswordValues() {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(POM_WITH_CREDENTIALS);
        assertThat(result.pomContent()).contains("<db.password>***MASKED***</db.password>");
        assertThat(result.pomContent()).doesNotContain("superSecret123");
    }

    @Test
    void masksTokenValues() {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(POM_WITH_CREDENTIALS);
        assertThat(result.pomContent()).contains("<api.token>***MASKED***</api.token>");
        assertThat(result.pomContent()).doesNotContain("tok_abc123xyz");
    }

    @Test
    void preservesPropertyPlaceholders() {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(POM_WITH_PLACEHOLDERS);
        assertThat(result.pomContent()).contains("${env.DB_PASSWORD}");
        assertThat(result.pomContent()).contains("${env.API_TOKEN}");
    }

    @Test
    void cleanPomUnchanged() {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(CLEAN_POM);
        assertThat(result.pomContent()).isEqualTo(CLEAN_POM);
        assertThat(result.detectedPatterns()).isEmpty();
    }

    @Test
    void sanitizedPomStillParseable() throws Exception {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(POM_WITH_CREDENTIALS);
        // Should still be valid XML that can be parsed
        MigrationData.PomAnalysis pom = MigrationData.parsePomContent(result.pomContent());
        assertThat(pom.camelVersion()).isEqualTo("4.10.0");
    }

    @Test
    void sanitizesMultiplePatterns() {
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(POM_WITH_MULTIPLE_SENSITIVE);
        assertThat(result.pomContent()).doesNotContain("myAppSecret");
        assertThat(result.pomContent()).doesNotContain("key_12345");
        assertThat(result.pomContent()).doesNotContain("AKIA1234567890");
        assertThat(result.detectedPatterns().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void caseInsensitiveDetection() {
        String pom = "<project><properties><PASSWORD>secret</PASSWORD></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).isNotEmpty();
    }

    @Test
    void detectsAccessKeyElement() {
        String pom = "<project><properties><accessKey>AKIA123</accessKey></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("accessKey"));
    }

    @Test
    void detectsPassphraseElement() {
        String pom = "<project><properties><passphrase>my-passphrase</passphrase></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("passphrase"));
    }

    @Test
    void detectsConnectionStringElement() {
        String pom
                = "<project><properties><connectionString>Server=myserver;Password=secret123</connectionString></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("connectionString"));
    }

    // ---- CDATA tests ----

    @Test
    void detectsCdataWrappedSecrets() {
        String pom = "<project><properties><db.password><![CDATA[superSecret123]]></db.password></properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).anyMatch(f -> f.contains("password"));
    }

    @Test
    void masksCdataWrappedSecrets() {
        String pom = "<project><properties><db.password><![CDATA[superSecret123]]></db.password></properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).doesNotContain("superSecret123");
        assertThat(result.pomContent()).contains("<db.password>***MASKED***</db.password>");
    }

    @Test
    void preservesPlaceholderInsideCdata() {
        String pom = "<project><properties><db.password><![CDATA[${env.DB_PASSWORD}]]></db.password></properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).contains("${env.DB_PASSWORD}");
        assertThat(result.detectedPatterns()).isEmpty();
    }

    // ---- Camel property placeholder tests ----

    @Test
    void ignoresCamelPropertyPlaceholders() {
        String pom = "<project><properties>"
                     + "<db.password>{{vault:db/password}}</db.password>"
                     + "<api.token>{{my.token}}</api.token>"
                     + "</properties></project>";
        List<String> findings = PomSanitizer.detectSensitiveContent(pom);
        assertThat(findings).isEmpty();
    }

    @Test
    void preservesCamelPropertyPlaceholders() {
        String pom = "<project><properties><db.password>{{vault:db/password}}</db.password></properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).contains("{{vault:db/password}}");
        assertThat(result.detectedPatterns()).isEmpty();
    }

    @Test
    void masksPartialCamelPlaceholder() {
        String pom = "<project><properties><db.password>{{notClosed</db.password></properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).contains("***MASKED***");
    }

    // ---- URL credential tests ----

    @Test
    void detectsUrlEmbeddedCredentials() {
        String pom = "<project><properties>"
                     + "<db.url>jdbc:mysql://admin:s3cret@db.example.com:3306/mydb</db.url>"
                     + "</properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).doesNotContain("s3cret");
        assertThat(result.pomContent()).contains("://admin:***MASKED***@");
        assertThat(result.detectedPatterns()).anyMatch(f -> f.contains("URL credential"));
    }

    @Test
    void elementPatternTakesPrecedenceOverUrlPattern() {
        String pom = "<project><properties>"
                     + "<db.password>jdbc:mysql://admin:s3cret@host/db</db.password>"
                     + "</properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).contains("<db.password>***MASKED***</db.password>");
        assertThat(result.pomContent()).doesNotContain("s3cret");
    }

    @Test
    void noFalsePositiveOnPortNumbers() {
        String pom = "<project><properties>"
                     + "<db.url>http://localhost:8080/api</db.url>"
                     + "</properties></project>";
        PomSanitizer.SanitizationResult result = PomSanitizer.sanitize(pom);
        assertThat(result.pomContent()).contains("http://localhost:8080/api");
        assertThat(result.detectedPatterns()).isEmpty();
    }

    // ---- Process helper tests ----

    @Test
    void processReturnsSingleSummaryWarning() {
        PomSanitizer.ProcessedPom result = PomSanitizer.process(POM_WITH_CREDENTIALS, null);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("db.password");
        assertThat(result.warnings().get(0)).contains("api.token");
    }

    @Test
    void processSkipsSanitizationWhenFalse() {
        PomSanitizer.ProcessedPom result = PomSanitizer.process(POM_WITH_CREDENTIALS, false);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.content()).isEqualTo(POM_WITH_CREDENTIALS);
    }

    @Test
    void processNoWarningsForCleanPom() {
        PomSanitizer.ProcessedPom result = PomSanitizer.process(CLEAN_POM, null);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.content()).isEqualTo(CLEAN_POM);
    }
}
