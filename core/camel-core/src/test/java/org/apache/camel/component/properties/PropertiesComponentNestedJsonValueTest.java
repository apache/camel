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
package org.apache.camel.component.properties;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A property value without {{ that contains }} (such as JSON that ends with a nested object) is text.
 */
public class PropertiesComponentNestedJsonValueTest extends ContextTestSupport {

    @Test
    public void testJsonValue() {
        Properties props = new Properties();
        props.put("json", "{\"a\":{\"b\":1}}");
        props.put("ref", "{{json}}");
        props.put("mixed", "{\"a\":{{json}}}");
        context.getPropertiesComponent().setInitialProperties(props);

        assertThat(context.resolvePropertyPlaceholders("{{json}}")).isEqualTo("{\"a\":{\"b\":1}}");
        assertThat(context.resolvePropertyPlaceholders("{{ref}}")).isEqualTo("{\"a\":{\"b\":1}}");
        assertThat(context.resolvePropertyPlaceholders("{{mixed}}")).isEqualTo("{\"a\":{\"a\":{\"b\":1}}}");
    }

    @Test
    public void testJsonValueWithDoubleCurlyBrackets() {
        // a value with {{ that is not a Camel placeholder needs nested=false or escaping
        Properties props = new Properties();
        props.put("template", "{\"query\":{\"match\":{\"body\":\"Hello {{name}}\"}}}");
        props.put("escaped", "{\"query\":{\"match\":{\"body\":\"Hello \\{{name\\}}\"}}}");
        context.getPropertiesComponent().setInitialProperties(props);

        assertThat(context.resolvePropertyPlaceholders("{{template?nested=false}}"))
                .isEqualTo("{\"query\":{\"match\":{\"body\":\"Hello {{name}}\"}}}");
        assertThat(context.resolvePropertyPlaceholders("{{escaped}}"))
                .isEqualTo("{\"query\":{\"match\":{\"body\":\"Hello {{name}}\"}}}");
    }

    @Test
    public void testRouteTemplateParameter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("setBodyTemplate")
                        .templateParameter("expression")
                        .from("direct:{{routeId}}")
                        .setBody().simple("{{expression}}");
            }
        });
        context.addRouteFromTemplate("templated", "setBodyTemplate",
                Map.of("routeId", "templated", "expression", "{\"title\":${header.title}}"));
        context.start();

        Object out = template.requestBodyAndHeader("direct:templated", null, "title", "\"Hello\"");
        assertThat(out).isEqualTo("{\"title\":\"Hello\"}");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
