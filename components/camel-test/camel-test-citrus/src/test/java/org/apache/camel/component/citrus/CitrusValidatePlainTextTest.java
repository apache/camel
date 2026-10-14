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
package org.apache.camel.component.citrus;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.citrusframework.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitrusValidatePlainTextTest extends CamelTestSupport {

    @Test
    void shouldValidatePlainTextBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=plaintext", CitrusEndpoint.class);
        citrus.allMessages().body("Hello Camel!");
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Camel!");

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCount()).isEqualTo(1);
    }

    @Test
    void shouldFailOnPlainTextMismatch() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=plaintext", CitrusEndpoint.class);
        citrus.allMessages().body("Hello Camel!");
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", "Goodbye Camel!");

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldValidateDefaultMessageType() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate", CitrusEndpoint.class);
        citrus.allMessages().body("Default text");
        citrus.expectedMessageCount(1);

        template.sendBody("direct:default", "Default text");

        citrus.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:validate?messageType=plaintext");
                from("direct:default").to("citrus:validate");
            }
        };
    }
}
