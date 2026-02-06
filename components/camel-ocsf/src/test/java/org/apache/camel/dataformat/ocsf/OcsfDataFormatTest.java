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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.ocsf.model.DetectionFinding;
import org.apache.camel.dataformat.ocsf.model.FindingInfo;
import org.apache.camel.dataformat.ocsf.model.OcsfEvent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OcsfDataFormatTest extends CamelTestSupport {

    @Test
    void testMarshalOcsfEvent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        OcsfEvent event = new OcsfEvent();
        event.setClassUid(OcsfConstants.CLASS_DETECTION_FINDING);
        event.setClassName("Detection Finding");
        event.setCategoryUid(OcsfConstants.CATEGORY_FINDINGS);
        event.setCategoryName("Findings");
        event.setActivityId(OcsfConstants.ACTIVITY_CREATE);
        event.setSeverityId(OcsfConstants.SEVERITY_HIGH);
        // time is Long in the generated class (milliseconds since epoch)
        event.setTime(System.currentTimeMillis());
        event.setMessage("Test security event");

        template.sendBody("direct:marshal", event);

        mock.assertIsSatisfied();

        String json = mock.getExchanges().get(0).getIn().getBody(String.class);
        assertThat(json).contains("\"class_uid\":2004");
        assertThat(json).contains("\"severity_id\":4");
        assertThat(json).contains("\"message\":\"Test security event\"");
    }

    @Test
    void testUnmarshalOcsfEvent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String json = """
                {
                    "class_uid": 2004,
                    "class_name": "Detection Finding",
                    "category_uid": 2,
                    "category_name": "Findings",
                    "activity_id": 1,
                    "severity_id": 4,
                    "time": 1706000000,
                    "message": "Suspicious activity detected"
                }
                """;

        template.sendBody("direct:unmarshal", json);

        mock.assertIsSatisfied();

        OcsfEvent event = mock.getExchanges().get(0).getIn().getBody(OcsfEvent.class);
        assertThat(event.getClassUid()).isEqualTo(2004);
        assertThat(event.getSeverityId()).isEqualTo(4);
        assertThat(event.getMessage()).isEqualTo("Suspicious activity detected");
    }

    @Test
    void testMarshalDetectionFinding() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        DetectionFinding finding = new DetectionFinding();
        // DetectionFinding contains finding-specific attributes
        // Base event fields like activity_id, severity_id are captured via additionalProperties
        finding.setAdditionalProperty("activity_id", OcsfConstants.ACTIVITY_CREATE);
        finding.setAdditionalProperty("severity_id", OcsfConstants.SEVERITY_CRITICAL);
        finding.setAdditionalProperty("time", (int) (System.currentTimeMillis() / 1000));
        finding.setAdditionalProperty("class_uid", OcsfConstants.CLASS_DETECTION_FINDING);
        finding.setIsAlert(true);
        finding.setRiskLevelId(Integer.valueOf(4));
        finding.setRiskLevel("High");

        FindingInfo info = new FindingInfo();
        info.setUid("finding-123");
        info.setTitle("Malware Detected");
        info.setDesc("Potential ransomware detected on endpoint");
        finding.setFindingInfo(info);

        template.sendBody("direct:marshal", finding);

        mock.assertIsSatisfied();

        String json = mock.getExchanges().get(0).getIn().getBody(String.class);
        assertThat(json).contains("\"class_uid\":2004");
        assertThat(json).contains("\"is_alert\":true");
        assertThat(json).contains("\"title\":\"Malware Detected\"");
    }

    @Test
    void testUnmarshalDetectionFinding() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshalFinding");
        mock.expectedMessageCount(1);

        String json = """
                {
                    "class_uid": 2004,
                    "class_name": "Detection Finding",
                    "category_uid": 2,
                    "category_name": "Findings",
                    "activity_id": 1,
                    "severity_id": 5,
                    "time": 1706000000,
                    "is_alert": true,
                    "risk_level": "Critical",
                    "risk_level_id": 5,
                    "finding_info": {
                        "uid": "finding-456",
                        "title": "Data Exfiltration Attempt",
                        "desc": "Unusual outbound data transfer detected"
                    }
                }
                """;

        template.sendBody("direct:unmarshalFinding", json);

        mock.assertIsSatisfied();

        DetectionFinding finding = mock.getExchanges().get(0).getIn().getBody(DetectionFinding.class);
        // Base event fields are captured in additionalProperties
        assertThat(finding.getAdditionalProperties().get("class_uid")).isEqualTo(2004);
        assertThat(finding.getAdditionalProperties().get("severity_id")).isEqualTo(5);
        assertThat(finding.getIsAlert()).isTrue();
        assertThat(finding.getRiskLevel()).isEqualTo("Critical");
        assertThat(finding.getFindingInfo()).isNotNull();
        assertThat(finding.getFindingInfo().getTitle()).isEqualTo("Data Exfiltration Attempt");
    }

    @Test
    void testUnmarshalWithUnknownProperties() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        // JSON with properties not defined in our POJOs
        String json = """
                {
                    "class_uid": 2004,
                    "severity_id": 3,
                    "time": 1706000000,
                    "unknown_property": "should be captured",
                    "another_unknown": 123
                }
                """;

        template.sendBody("direct:unmarshal", json);

        mock.assertIsSatisfied();

        OcsfEvent event = mock.getExchanges().get(0).getIn().getBody(OcsfEvent.class);
        assertThat(event.getClassUid()).isEqualTo(2004);
        assertThat(event.getSeverityId()).isEqualTo(3);
        // Unknown properties should be captured in additionalProperties
        assertThat(event.getAdditionalProperties()).containsKey("unknown_property");
        assertThat(event.getAdditionalProperties().get("unknown_property")).isEqualTo("should be captured");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal")
                        .marshal().ocsf()
                        .to("mock:marshal");

                from("direct:unmarshal")
                        .unmarshal().ocsf()
                        .to("mock:unmarshal");

                from("direct:unmarshalFinding")
                        .unmarshal().ocsf(DetectionFinding.class)
                        .to("mock:unmarshalFinding");
            }
        };
    }
}
