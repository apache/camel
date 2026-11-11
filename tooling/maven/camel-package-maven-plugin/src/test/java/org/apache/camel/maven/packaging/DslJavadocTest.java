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
package org.apache.camel.maven.packaging;

import org.apache.camel.tooling.util.JavadocHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DslJavadocTest {

    private static final String DESCRIPTION = "Matches {@code *}/{@code *} media types.";

    @Test
    void testEndpointJavadocEscapesCommentEnd() {
        String description = JavadocHelper.sanitizeDescription(DESCRIPTION, false);
        assertThat(description).isEqualTo("Matches */* media types.");
        assertThat(new EndpointDslMojo(null, null).javadoc("", description))
                .isEqualTo("/**\n * Matches *&#47;* media types.\n */");
    }

    @Test
    void testComponentJavadocEscapesCommentEnd() {
        String description = JavadocHelper.sanitizeDescription(DESCRIPTION, false);
        assertThat(description).isEqualTo("Matches */* media types.");
        assertThat(new ComponentDslMojo(null, null).javadoc("", description))
                .isEqualTo("/**\n * Matches *&#47;* media types.\n */");
    }
}
