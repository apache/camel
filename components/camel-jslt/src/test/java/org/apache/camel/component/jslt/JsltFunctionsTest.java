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
package org.apache.camel.component.jslt;

import java.util.Collection;
import java.util.Collections;

import com.schibsted.spt.data.jslt.Function;
import com.schibsted.spt.data.jslt.FunctionUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Unit test using user defined functions
 */
public class JsltFunctionsTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getPropertiesComponent().setLocation("ref:prop");

        context.getComponent("jslt", JsltComponent.class).setFunctions(createFunctions());

        return context;
    }

    private Collection<Function> createFunctions() throws ClassNotFoundException {
        return Collections.singleton(
                FunctionUtils.wrapStaticMethod("power", "java.lang.Math", "pow"));
    }

    @Test
    public void testSimpleFunction() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("1024.0");

        sendBody("direct://start", "{}", Collections.singletonMap(JsltConstants.HEADER_JSLT_STRING, "power(2, 10)"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                JsltComponent js = context.getComponent("jslt", JsltComponent.class);
                js.setAllowTemplateFromHeader(true);

                from("direct://start")
                        .to("jslt:dummy")
                        .to("mock:result");
            }
        };
    }

}
