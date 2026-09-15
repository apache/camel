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
package org.apache.camel.component.kamelet;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that kamelet parameters containing URL-special characters are preserved intact when the kamelet is invoked
 * via {@code toD}, matching the behaviour of the static {@code to} DSL (CAMEL-24747).
 *
 * <p>
 * Prior to the fix, {@code SendDynamicProcessor.prepareRecipient()} created a
 * {@link org.apache.camel.support.NormalizedUri} whose normalized (URL-encoded) form was then passed to
 * {@code doGetEndpoint} as if it were the raw URI. Components that declare {@code useRawUri()=true} (such as
 * {@link KameletComponent}) therefore received the encoded form ({@code http%3A%2F%2F…}) instead of the original value.
 */
class KameletToDUrlEncodingTest extends CamelTestSupport {

    /**
     * A parameter value that contains URL-special characters: {@code %}, {@code +}, {@code ?} and {@code =}. These must
     * survive the round-trip through {@code toD} unmodified.
     */
    private static final String PARAM_WITH_SPECIAL_CHARS = "http://example.com?key=abc%+def";

    @Test
    void toDPreservesSpecialCharsLikeTo() {
        // Use toD with a literal URI containing URL-special characters in a parameter value.
        // The result should be identical to what the static `to` DSL produces.
        String resultTo = template.requestBody("direct:via-to", (Object) null, String.class);
        String resultToD = template.requestBody("direct:via-tod", (Object) null, String.class);

        assertThat(resultTo)
                .as("to: parameter value must not be URL-encoded")
                .isEqualTo(PARAM_WITH_SPECIAL_CHARS);

        assertThat(resultToD)
                .as("toD: parameter value must equal what to produces (CAMEL-24747)")
                .isEqualTo(resultTo);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo-uri")
                        .templateParameter("uri")
                        .from("kamelet:source")
                        .setBody().constant("{{uri}}");

                // static `to` — baseline: known to pass the raw URI correctly
                from("direct:via-to")
                        .to("kamelet:echo-uri?uri=" + PARAM_WITH_SPECIAL_CHARS);

                // dynamic `toD` — was broken before the fix (CAMEL-24747)
                from("direct:via-tod")
                        .toD("kamelet:echo-uri?uri=" + PARAM_WITH_SPECIAL_CHARS);
            }
        };
    }
}
