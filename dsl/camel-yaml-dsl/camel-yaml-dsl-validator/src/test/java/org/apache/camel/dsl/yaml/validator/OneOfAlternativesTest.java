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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25238: the branches of a pick-one construct each require their own key, so a file that chose one of them
 * collects an error for every key it did not write. The one it did write is missing from them, because that branch got
 * past its required check, so the file is told it needs a key it does not.
 */
class OneOfAlternativesTest {

    private static List<String> validate(String content) throws Exception {
        YamlValidator v = new YamlValidator();
        return YamlValidator.describeAll(content, v.validate(content));
    }

    @Test
    void twoBranchesUnmarshallingWithDifferentDataFormats() throws Exception {
        // the shape of fhir-sink.kamelet.yaml: each when branch unmarshals with a different fhir data format, and the
        // second was told "required property 'fhirJson' not found"
        assertThat(validate("""
                - route:
                    id: fhir
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                              - simple: "${header.encoding} == 'JSON'"
                                steps:
                                  - unmarshal:
                                      fhirJson:
                                        fhirVersion: "{{fhirVersion}}"
                              - simple: "${header.encoding} == 'XML'"
                                steps:
                                  - unmarshal:
                                      fhirXml:
                                        fhirVersion: "{{fhirVersion}}"
                """)).isEmpty();
    }

    @Test
    void oneBranchChosenInSeveralPlacesIsStillFine() throws Exception {
        assertThat(validate("""
                - route:
                    id: two
                    from:
                      uri: timer:tick
                      steps:
                        - unmarshal:
                            json:
                              library: Jackson
                        - marshal:
                            csv: {}
                """)).isEmpty();
    }

    @Test
    void choosingNothingIsStillReported() throws Exception {
        // the construct's own error says the file has to pick one, and that must survive
        assertThat(validate("""
                - route:
                    id: none
                    from:
                      uri: timer:tick
                      steps:
                        - unmarshal: {}
                """)).isNotEmpty();
    }

    @Test
    void choosingSomethingThatIsNotADataFormatIsStillReported() throws Exception {
        assertThat(validate("""
                - route:
                    id: bogus
                    from:
                      uri: timer:tick
                      steps:
                        - unmarshal:
                            notADataFormat: {}
                """)).isNotEmpty();
    }

    @Test
    void anUnrelatedMistakeIsUntouched() throws Exception {
        assertThat(validate("""
                - route:
                    id: typo
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            mesage: "typo"
                """)).anyMatch(e -> e.contains("did you mean 'message'"));
    }
}
