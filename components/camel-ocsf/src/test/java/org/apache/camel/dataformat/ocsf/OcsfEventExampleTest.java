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
package org.apache.camel.dataformat.ocsf;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.ocsf.model.Attack;
import org.apache.camel.dataformat.ocsf.model.DetectionFinding;
import org.apache.camel.dataformat.ocsf.model.FindingInfo;
import org.apache.camel.dataformat.ocsf.model.Metadata;
import org.apache.camel.dataformat.ocsf.model.OcsfEvent;
import org.apache.camel.dataformat.ocsf.model.Product;
import org.apache.camel.dataformat.ocsf.model.Remediation;
import org.apache.camel.dataformat.ocsf.model.ResourceDetails;
import org.apache.camel.dataformat.ocsf.model.Tactic;
import org.apache.camel.dataformat.ocsf.model.Technique;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Examples showing OCSF event structure and how to work with it in Camel.
 */
public class OcsfEventExampleTest extends CamelTestSupport {

    /**
     * Parse a complete OCSF Detection Finding from JSON file and demonstrate accessing all fields.
     */
    @Test
    void testParseCompleteOcsfFinding() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parsed");
        mock.expectedMessageCount(1);

        // Load the example OCSF event from resources
        InputStream is = getClass().getResourceAsStream("/ocsf-detection-finding-example.json");
        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        template.sendBody("direct:parse-finding", json);

        mock.assertIsSatisfied();

        DetectionFinding finding = mock.getExchanges().get(0).getIn().getBody(DetectionFinding.class);

        // Verify finding-specific fields
        assertThat(finding.getIsAlert()).isTrue();
        assertThat(finding.getRiskLevel()).isEqualTo("High");
        assertThat(finding.getRiskLevelId()).isEqualTo(4);
        assertThat(finding.getRiskScore()).isEqualTo(78);
        assertThat(finding.getConfidence()).isEqualTo("High");
        assertThat(finding.getConfidenceScore()).isEqualTo(85);

        // Verify finding info
        assertThat(finding.getFindingInfo()).isNotNull();
        assertThat(finding.getFindingInfo().getTitle()).contains("CryptoCurrency");
        assertThat(finding.getFindingInfo().getDesc()).contains("Bitcoin-related activity");

        // Verify MITRE ATT&CK mapping (attacks are in FindingInfo in OCSF 1.7.0)
        assertThat(finding.getFindingInfo().getAttacks()).hasSize(1);
        Attack attack = finding.getFindingInfo().getAttacks().get(0);
        assertThat(attack.getTactic().getName()).isEqualTo("Impact");
        assertThat(attack.getTactic().getUid()).isEqualTo("TA0040");
        assertThat(attack.getTechnique().getName()).isEqualTo("Resource Hijacking");
        assertThat(attack.getTechnique().getUid()).isEqualTo("T1496");

        // Verify resources
        assertThat(finding.getResources()).hasSize(1);
        ResourceDetails resource = finding.getResources().get(0);
        assertThat(resource.getType()).isEqualTo("AWS::EC2::Instance");
        assertThat(resource.getName()).isEqualTo("production-web-server");

        // Verify remediation
        assertThat(finding.getRemediation()).isNotNull();
        assertThat(finding.getRemediation().getDesc()).contains("Investigate the EC2 instance");

        // Base event fields are in additionalProperties
        assertThat(finding.getAdditionalProperties().get("class_uid")).isEqualTo(2004);
        assertThat(finding.getAdditionalProperties().get("severity_id")).isEqualTo(4);
        assertThat(finding.getAdditionalProperties().get("severity")).isEqualTo("High");

        // Verify cloud info in additionalProperties
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> cloud = (java.util.Map<String, Object>) finding.getAdditionalProperties().get("cloud");
        assertThat(cloud.get("provider")).isEqualTo("AWS");
        assertThat(cloud.get("region")).isEqualTo("us-east-1");
    }

    /**
     * Build a complete OCSF Detection Finding programmatically and marshal to JSON.
     */
    @Test
    void testBuildOcsfFindingProgrammatically() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:json-output");
        mock.expectedMessageCount(1);

        // Create a Detection Finding for a SQL Injection attempt
        DetectionFinding finding = new DetectionFinding();

        // Set base event fields via additionalProperties
        finding.setAdditionalProperty("class_uid", OcsfConstants.CLASS_DETECTION_FINDING);
        finding.setAdditionalProperty("class_name", "Detection Finding");
        finding.setAdditionalProperty("category_uid", OcsfConstants.CATEGORY_FINDINGS);
        finding.setAdditionalProperty("category_name", "Findings");
        finding.setAdditionalProperty("activity_id", OcsfConstants.ACTIVITY_CREATE);
        finding.setAdditionalProperty("activity_name", "Create");
        finding.setAdditionalProperty("severity_id", OcsfConstants.SEVERITY_HIGH);
        finding.setAdditionalProperty("severity", "High");
        finding.setAdditionalProperty("time", 1706198400);
        finding.setAdditionalProperty("message", "SQL Injection attempt detected in web application");

        // Set finding-specific fields
        finding.setIsAlert(true);
        finding.setRiskLevel("High");
        finding.setRiskLevelId(Integer.valueOf(4));
        finding.setRiskScore(85);
        finding.setConfidence("High");
        finding.setConfidenceId(Integer.valueOf(3));
        finding.setConfidenceScore(90);

        // Finding info
        FindingInfo info = new FindingInfo();
        info.setUid("finding-sql-injection-001");
        info.setTitle("SQL Injection Attempt - UNION SELECT");
        info.setDesc("Detected SQL injection attempt using UNION SELECT in user input parameter. "
                     + "Attack originated from IP 203.0.113.50 targeting /api/users endpoint.");
        info.setTypes(Arrays.asList("Application/Injection", "TTPs/Initial Access"));
        info.setFirstSeenTime(1706194800000L);
        info.setLastSeenTime(1706198400000L);
        finding.setFindingInfo(info);

        // MITRE ATT&CK mapping (attacks are set on FindingInfo in OCSF 1.7.0)
        Attack attack = new Attack();
        Tactic tactic = new Tactic();
        tactic.setName("Initial Access");
        tactic.setUid("TA0001");
        attack.setTactic(tactic);

        Technique technique = new Technique();
        technique.setName("Exploit Public-Facing Application");
        technique.setUid("T1190");
        attack.setTechnique(technique);
        attack.setVersion("14.0");
        info.setAttacks(Arrays.asList(attack));

        // Evidence (using Map since evidences is a list of generic objects in OCSF)
        java.util.Map<String, Object> evidence = new java.util.HashMap<>();
        evidence.put("http_request", java.util.Map.of(
                "method", "GET",
                "url", "/api/users?id=1' UNION SELECT username,password FROM users--",
                "user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "source_ip", "203.0.113.50"));
        finding.setAdditionalProperty("evidences", Arrays.asList(evidence));

        // Remediation
        Remediation remediation = new Remediation();
        remediation.setDesc("1. Block the source IP 203.0.113.50\n"
                            + "2. Review and patch the vulnerable endpoint\n"
                            + "3. Implement parameterized queries\n"
                            + "4. Enable WAF SQL injection rules");
        remediation.setReferences(Arrays.asList(
                "https://owasp.org/www-community/attacks/SQL_Injection",
                "https://attack.mitre.org/techniques/T1190/"));
        finding.setRemediation(remediation);

        // Resource (the affected web application)
        ResourceDetails resource = new ResourceDetails();
        resource.setUid("arn:aws:elasticbeanstalk:us-east-1:123456789012:environment/my-app/production");
        resource.setName("production-web-app");
        resource.setType("AWS::ElasticBeanstalk::Environment");
        resource.setAdditionalProperty("region", "us-east-1");
        finding.setResources(Arrays.asList(resource));

        // Metadata
        Metadata metadata = new Metadata();
        metadata.setVersion("1.7.0");
        Product product = new Product();
        product.setName("Application WAF");
        product.setVendorName("MyCompany");
        product.setVersion("2.5.0");
        metadata.setProduct(product);
        finding.setAdditionalProperty("metadata", metadata);

        // Send to marshal
        template.sendBody("direct:build-finding", finding);

        mock.assertIsSatisfied();

        String json = mock.getExchanges().get(0).getIn().getBody(String.class);

        // Verify the JSON output
        assertThat(json).contains("\"class_uid\":2004");
        assertThat(json).contains("\"is_alert\":true");
        assertThat(json).contains("SQL Injection Attempt");
        assertThat(json).contains("T1190");
        assertThat(json).contains("TA0001");
        assertThat(json).contains("203.0.113.50");
    }

    /**
     * Parse and work with a basic OCSF Event (not a specific class like DetectionFinding).
     */
    @Test
    void testParseGenericOcsfEvent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:generic-event");
        mock.expectedMessageCount(1);

        // A simple OCSF event (could be any event class)
        String json = """
                {
                    "class_uid": 1001,
                    "class_name": "File System Activity",
                    "category_uid": 1,
                    "category_name": "System Activity",
                    "activity_id": 1,
                    "activity_name": "Create",
                    "severity_id": 2,
                    "severity": "Informational",
                    "time": 1706198400,
                    "message": "File created: /var/log/application.log",
                    "metadata": {
                        "version": "1.7.0",
                        "product": {
                            "name": "File Integrity Monitor",
                            "vendor_name": "SecurityTools"
                        }
                    },
                    "file": {
                        "name": "application.log",
                        "path": "/var/log/application.log",
                        "type": "Regular File",
                        "type_id": 1,
                        "size": 0,
                        "created_time": 1706198400
                    },
                    "actor": {
                        "user": {
                            "name": "app-user",
                            "uid": "1001"
                        },
                        "process": {
                            "name": "myapp",
                            "pid": 12345
                        }
                    }
                }
                """;

        template.sendBody("direct:parse-generic", json);

        mock.assertIsSatisfied();

        OcsfEvent event = mock.getExchanges().get(0).getIn().getBody(OcsfEvent.class);

        // Verify base event fields
        assertThat(event.getClassUid()).isEqualTo(1001);
        assertThat(event.getClassName()).isEqualTo("File System Activity");
        assertThat(event.getCategoryUid()).isEqualTo(1);
        assertThat(event.getSeverityId()).isEqualTo(2);
        assertThat(event.getMessage()).isEqualTo("File created: /var/log/application.log");

        // Metadata is a proper object
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata().getVersion()).isEqualTo("1.7.0");
        assertThat(event.getMetadata().getProduct().getName()).isEqualTo("File Integrity Monitor");

        // Fields not in OcsfEvent schema go to additionalProperties
        assertThat(event.getAdditionalProperties()).containsKey("file");
        assertThat(event.getAdditionalProperties()).containsKey("actor");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:parse-finding")
                        .unmarshal().ocsf(DetectionFinding.class)
                        .to("mock:parsed");

                from("direct:build-finding")
                        .marshal().ocsf()
                        .to("mock:json-output");

                from("direct:parse-generic")
                        .unmarshal().ocsf(OcsfEvent.class)
                        .to("mock:generic-event");
            }
        };
    }
}
