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
package org.apache.camel.dsl.yaml.validator;

import java.util.List;

import com.networknt.schema.Error;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25194: a .kamelet.yaml is a YAML object and not the list of entries a route file is, so the route schema failed
 * every one of them as "object found, array expected" -- and because the tools validate before they write, the file
 * could not be written at all.
 */
class KameletValidationTest {

    private static final String WORKING = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: content-filter-action
              labels:
                camel.apache.org/kamelet.type: action
            spec:
              definition:
                title: Content Filter
                properties:
                  allowlist:
                    title: Allowlist
                    type: string
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - setBody:
                        expression:
                          jq:
                            expression: 'with_entries(select(.key as $k | "{{allowlist}}" | split(",") | index($k)))'
            """;

    private static List<String> validate(String content) throws Exception {
        YamlValidator v = new YamlValidator();
        List<Error> errors = v.validate(content);
        return YamlValidator.describeAll(content, errors);
    }

    @Test
    void aKameletThatRunsIsValid() throws Exception {
        // this is the file of the issue: camel run logs the filtered body for it
        assertThat(validate(WORKING)).isEmpty();
    }

    @Test
    void aMistakeInTheTemplateIsReportedInTheTemplate() throws Exception {
        // jq is a language, not a step of its own: the error belongs to the step, not to the envelope
        String broken = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: content-filter-action
                spec:
                  definition:
                    title: Content Filter
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - jq:
                            expression: keep
                """;
        List<String> errors = validate(broken);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .contains("Line 12")
                .contains("/spec/template/from/steps/0")
                .contains("property 'jq' is not defined in the schema")
                .doesNotContain("array expected");
    }

    @Test
    void aTemplateMayDeclareBeansBesideItsFrom() throws Exception {
        // an entry of a route file is exactly one of route/from/beans/...; a Kamelet template is not, and 60 of the
        // 250 kamelets of the library declare a bean beside their from, counter-source among them
        assertThat(validate("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: counter-source
                spec:
                  template:
                    beans:
                      - name: counter
                        type: java.util.concurrent.atomic.AtomicInteger
                    from:
                      uri: timer:counter
                      steps:
                        - bean:
                            ref: "{{counter}}"
                            method: getAndIncrement
                """)).isEmpty();
    }

    @Test
    void aPropertyOfTheDefinitionIsNotAStepOfTheSameName() throws Exception {
        // spec.definition is the JSON schema of the Kamelet's properties: a property named delay is not the Delay EIP,
        // which is what aws-s3-source and eight others of the library were told
        assertThat(validate("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: aws-s3-source
                spec:
                  definition:
                    title: AWS S3 Source
                    properties:
                      delay:
                        title: Delay
                        type: integer
                        default: 500
                  template:
                    from:
                      uri: timer:tick
                      steps:
                        - to: "kamelet:sink"
                """)).isEmpty();
    }

    @Test
    void aPlaceholderWhereAnEnumerationIsExpectedIsAccepted() throws Exception {
        // a Kamelet template is made of placeholders, and the runtime resolves one before it looks at the value:
        // fhir-source has fhirVersion: "{{fhirVersion}}" where the schema lists the six versions
        assertThat(validate("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: fhir-source
                spec:
                  template:
                    from:
                      uri: timer:tick
                      steps:
                        - marshal:
                            fhirJson:
                              fhirVersion: "{{fhirVersion}}"
                """)).isEmpty();
    }

    @Test
    void aRouteFileIsStillValidatedAsAListOfEntries() throws Exception {
        assertThat(validate("""
                - route:
                    id: ok
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "hi"
                """)).isEmpty();
        assertThat(validate("""
                route:
                  id: notAList
                """)).anyMatch(e -> e.contains("array expected"));
    }

    @Test
    void theEnvelopeIsNotFailedForWhatCamelDoesNotRead() throws Exception {
        // Camel does not own the Kamelet CRD; annotations and extra spec keys are accepted as they come
        assertThat(validate(WORKING.replace("""
                metadata:
                  name: content-filter-action""",
                """
                        metadata:
                          name: content-filter-action
                          annotations:
                            camel.apache.org/catalog.version: "4.23.0\""""))).isEmpty();
    }
}
