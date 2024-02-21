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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import com.schibsted.spt.data.jslt.filters.JsltJsonFilter;
import com.schibsted.spt.data.jslt.filters.JsonFilter;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

public class JsltBigDecimalCustomObjectMapperTest extends CamelTestSupport {

    @BindToRegistry("customMapper")
    ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getPropertiesComponent().setLocation("ref:prop");

        context.getComponent("jslt", JsltComponent.class).setObjectFilter(createObjectFilter());

        return context;
    }

    @Test
    public void testJsltAsInputStream() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/useBigDecimal/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        sendBody("direct://start",
                ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jslt/useBigDecimal/input.json"));

        MockEndpoint.assertIsSatisfied(context);
    }

    private JsonFilter createObjectFilter() {
        Expression filterExpression = Parser.compileString(". != null and . != {}");
        return new JsltJsonFilter(filterExpression);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("jslt:org/apache/camel/component/jslt/useBigDecimal/transformation.json?prettyPrint=true&objectMapper=#customMapper")
                        .to("mock:result");
            }
        };
    }
}
