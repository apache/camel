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
package org.apache.camel.component.typesafeai;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.test.infra.typesafeai.mock.TypeSafeAiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** Route tests using a deterministic local System One mock. */
class TypeSafeAiExamplesTest extends TypeSafeAiExamplesSupport {
    @RegisterExtension
    TypeSafeAiService service = new TypeSafeAiService(null, null, null);

    @Override
    TypeSafeAiService service() {
        return service;
    }

    @Test
    void confidenceGateHandlesBothDecisions() {
        AtomicInteger requests = new AtomicInteger();
        service.setResponder(request -> {
            double confidence = requests.incrementAndGet() == 1 ? 0.9 : 0.2;
            return Map.of(
                    "is_urgent", Map.of("type", "noul", "noul", 0.9),
                    "department", Map.of("type", "choice", "choice", "billing", "confidence", confidence,
                            "probabilities", Map.of("billing", 1.0, "technical", 0.0, "sales", 0.0)),
                    "frustration", Map.of("type", "score", "score", 0, "confidence", 1.0,
                            "probabilities", Map.of("0", 1.0, "1", 0.0, "2", 0.0),
                            "legend", Map.of("0", "Calm", "1", "Frustrated", "2", "Very angry")));
        });

        Exchange automatic = template.request("direct:quickstart", e -> e.getMessage().setBody("Refund requested"));
        Exchange review = template.request("direct:quickstart", e -> e.getMessage().setBody("Refund requested"));
        assertThat(automatic.getException()).isNull();
        assertThat(review.getException()).isNull();
        assertThat(automatic.getMessage().getHeader("decision")).isEqualTo("automatic");
        assertThat(review.getMessage().getHeader("decision")).isEqualTo("review");
        assertThat(service.getRequests()).hasSize(2);
    }
}
