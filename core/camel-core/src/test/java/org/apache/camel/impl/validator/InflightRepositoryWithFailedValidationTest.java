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
package org.apache.camel.impl.validator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class InflightRepositoryWithFailedValidationTest extends ContextTestSupport {

    @Test
    public void testInflight() throws Exception {
        assertEquals(0, context.getInflightRepository().size());

        Exception e = null;
        try {
            template.sendBody("direct:start", "FAIL");
        } catch (Exception ex) {
            e = ex;
        }
        assertNotNull(e);

        assertEquals(0, context.getInflightRepository().size("first"));
        assertEquals(0, context.getInflightRepository().size("second"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator().type("simple").withExpression(bodyAs(String.class).contains("valid"));

                from("direct:start").routeId("first").to("direct:validation");

                from("direct:validation").routeId("second").inputTypeWithValidate("simple").to("mock:result");
            }
        };
    }
}
