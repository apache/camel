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
package org.apache.camel.component.fhir;

import java.util.List;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirValidateApiMethod;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirValidate} APIs. The class source won't be generated
 * again if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirValidateIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirValidateIT.class);
    private static final String PATH_PREFIX
            = FhirApiCollection.getCollection().getApiName(FhirValidateApiMethod.class).getName();

    @Test
    public void testResource() {
        Patient bobbyHebb = new Patient().addName(new HumanName().addGiven("Bobby").setFamily("Hebb"));
        bobbyHebb.getText().setStatus(Narrative.NarrativeStatus.GENERATED);
        bobbyHebb.getText().setDivAsString("<div>This is the narrative text</div>");

        // using org.hl7.fhir.instance.model.api.IBaseResource message body for single parameter "resource"
        MethodOutcome result = requestBody("direct://RESOURCE", bobbyHebb);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        OperationOutcome operationOutcome = (OperationOutcome) result.getOperationOutcome();
        assertNotNull(operationOutcome);

        List<OperationOutcomeIssueComponent> issue = operationOutcome.getIssue();
        assertNotNull(issue);
        assertEquals(1, issue.size());
        assertTrue(issue.get(0).getDiagnostics()
                .contains("No issues detected during validation"));
    }

    @Test
    public void testResourceAsString() {
        Patient bobbyHebb = new Patient().addName(new HumanName().addGiven("Bobby").setFamily("Hebb"));
        bobbyHebb.getText().setStatus(Narrative.NarrativeStatus.GENERATED);
        bobbyHebb.getText().setDivAsString("<div>This is the narrative text</div>");

        // using org.hl7.fhir.instance.model.api.IBaseResource message body for single parameter "resource"
        MethodOutcome result
                = requestBody("direct://RESOURCE_AS_STRING", this.fhirContext.newXmlParser().encodeResourceToString(bobbyHebb));

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        OperationOutcome operationOutcome = (OperationOutcome) result.getOperationOutcome();
        assertNotNull(operationOutcome);

        List<OperationOutcomeIssueComponent> issue = operationOutcome.getIssue();
        assertNotNull(issue);
        assertEquals(1, issue.size());
        assertTrue(issue.get(0).getDiagnostics()
                .contains("No issues detected during validation"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for resource
                from("direct://RESOURCE")
                        .to("fhir://" + PATH_PREFIX + "/resource?inBody=resource");

                // test route for resource
                from("direct://RESOURCE_AS_STRING")
                        .to("fhir://" + PATH_PREFIX + "/resource?inBody=resourceAsString");

            }
        };
    }
}
