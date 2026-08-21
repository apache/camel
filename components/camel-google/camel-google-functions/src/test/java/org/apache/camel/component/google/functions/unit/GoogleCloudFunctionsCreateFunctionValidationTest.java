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
package org.apache.camel.component.google.functions.unit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that createFunction reports the header it is missing instead of failing inside protobuf.
 */
class GoogleCloudFunctionsCreateFunctionValidationTest extends GoogleCloudFunctionsBaseTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:createFunction")
                        .to("google-functions://myCamelFunction?project=project123&location=location123"
                            + "&operation=createFunction");
            }
        };
    }

    @Test
    void aMissingEntryPointIsReported() {
        Exchange exchange = template.request("direct:createFunction", e -> {
        });

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The " + GoogleCloudFunctionsConstants.ENTRY_POINT + " header must be set for this operation");
    }

    @Test
    void aMissingRuntimeIsReported() {
        Exchange exchange = template.request("direct:createFunction",
                e -> e.getIn().setHeader(GoogleCloudFunctionsConstants.ENTRY_POINT, "myEntryPoint"));

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The " + GoogleCloudFunctionsConstants.RUNTIME + " header must be set for this operation");
    }

    @Test
    void aMissingSourceArchiveUrlIsReported() {
        Exchange exchange = template.request("direct:createFunction", e -> {
            e.getIn().setHeader(GoogleCloudFunctionsConstants.ENTRY_POINT, "myEntryPoint");
            e.getIn().setHeader(GoogleCloudFunctionsConstants.RUNTIME, "java17");
        });

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The " + GoogleCloudFunctionsConstants.SOURCE_ARCHIVE_URL
                            + " header must be set for this operation");
    }
}
