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
package org.apache.camel.component.dirigible;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirigibleJavaScriptComponentTest extends CamelTestSupport {

    @BindToRegistry("dirigibleJavaScriptInvokerMock")
    DirigibleJavaScriptInvokerMock invokerMock = new DirigibleJavaScriptInvokerMock();

    @Test
    public void testComponentExecution() throws Exception {
        String initialBody = "my test body";
        String result = template.requestBody("direct:callDirigibleScript", initialBody, String.class);

        String expectedBody = initialBody.toUpperCase();
        assertEquals(expectedBody, result, "Unexpected result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:callDirigibleScript").to("dirigible-java-script:dirigible-java-script-component/handler.mjs");
            }
        };
    }
}
