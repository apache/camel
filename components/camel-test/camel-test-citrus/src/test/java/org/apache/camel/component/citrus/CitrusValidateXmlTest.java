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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.citrusframework.validation.DefaultValidations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitrusValidateXmlTest extends CamelTestSupport {

    @Test
    void shouldValidateXmlBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=xml", CitrusEndpoint.class);
        citrus.allMessages().body("""
                <greeting>
                  <text>Hello World!</text>
                </greeting>
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                <greeting>
                  <text>Hello World!</text>
                </greeting>
                """);

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCount()).isEqualTo(1);
    }

    @Test
    void shouldValidateXpathExpressions() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=xml", CitrusEndpoint.class);
        citrus.allMessages().validate(new DefaultValidations().xpath()
                .expression("/greeting/text", "Hello World!")
                .expression("/greeting/author", "Camel"));
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                <greeting>
                  <text>Hello World!</text>
                  <author>Camel</author>
                </greeting>
                """);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldAutoDetectXmlMessageType() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate", CitrusEndpoint.class);
        citrus.allMessages().body("""
                <greeting>
                  <text>Hello World!</text>
                </greeting>
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:auto", """
                <greeting>
                  <text>Hello World!</text>
                </greeting>
                """);

        citrus.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:validate?messageType=xml");
                from("direct:auto").to("citrus:validate");
            }
        };
    }
}
