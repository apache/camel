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
package org.apache.camel.component.ai.resource;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResourceUriTemplateTest {

    @Test
    void matchesVariablesAsSinglePathSegments() {
        AiResourceUriTemplate template = new AiResourceUriTemplate("crm://customers/{id}/orders/{orderId}");

        assertThat(template.match("crm://customers/42/orders/9001"))
                .hasValue(Map.of("id", "42", "orderId", "9001"));
        assertThat(template.match("crm://customers/42/orders/9001/lines"))
                .isEmpty();
        assertThat(template.match("crm://customers/42/extra/9001"))
                .isEmpty();
    }

    @Test
    void supportsEmptyAuthorityUrisAndKeepsPercentEncoding() {
        AiResourceUriTemplate template = new AiResourceUriTemplate("camel:///reports/{reportId}");

        assertThat(template.match("camel:///reports/Q1%2F2026"))
                .hasValue(Map.of("reportId", "Q1%2F2026"));
    }

    @Test
    void matchesOnlyRfc3986PcharSegmentsAndRejectsUnsafeOrMalformedSegments() {
        AiResourceUriTemplate template = new AiResourceUriTemplate("crm://customers/{id}");

        assertThat(template.match("crm://customers/a-b._~!$&'()*+,;=:@%2F"))
                .hasValue(Map.of("id", "a-b._~!$&'()*+,;=:@%2F"));
        assertThat(template.match("crm://customers/42?x=1")).isEmpty();
        assertThat(template.match("crm://customers/42#fragment")).isEmpty();
        assertThat(template.match("crm://customers/.")).isEmpty();
        assertThat(template.match("crm://customers/..")).isEmpty();
        assertThat(template.match("crm://customers/a\nb")).isEmpty();
        assertThat(template.match("crm://customers/%2")).isEmpty();
    }

    @Test
    void detectsOverlappingTemplates() {
        AiResourceUriTemplate left = new AiResourceUriTemplate("crm://customers/{id}");
        AiResourceUriTemplate right = new AiResourceUriTemplate("crm://customers/{customerId}");
        AiResourceUriTemplate different = new AiResourceUriTemplate("crm://orders/{id}");

        assertThat(left.overlaps(right)).isTrue();
        assertThat(left.overlaps(different)).isFalse();
    }

    @Test
    void rejectsExpressionsThatAreNotWholePathSegments() {
        assertThatThrownBy(() -> new AiResourceUriTemplate("crm://customers/pre-{id}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole path segment");
    }

    @Test
    void rejectsDuplicateVariableNames() {
        assertThatThrownBy(() -> new AiResourceUriTemplate("crm://customers/{id}/orders/{id}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsVariableNamesThatDifferOnlyByCase() {
        assertThatThrownBy(() -> new AiResourceUriTemplate("camel:///orders/{id}/items/{ID}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("case-insensitive");
    }

    @Test
    void rejectsTemplatesWithoutVariables() {
        assertThatThrownBy(() -> new AiResourceUriTemplate("crm://customers/all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one path variable");
    }
}
