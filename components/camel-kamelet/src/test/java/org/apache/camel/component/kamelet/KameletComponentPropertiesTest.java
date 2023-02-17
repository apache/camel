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

import java.util.Properties;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.PropertiesHelper.asProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class KameletComponentPropertiesTest {
    private static final String KAMELET_COMPONENT_PROPERTY_PREFIX = "camel.component.kamelet.";

    @Test
    public void testComponentConfigurationAndConfigurationPrecedence() {
        Main main = new Main();
        try {

            main.setOverrideProperties(getTestProperties());
            main.setDefaultPropertyPlaceholderLocation("false");
            main.configure().addRoutesBuilder(createRouteBuilder());
            main.start();

            assertThat(
                    main.getCamelTemplate()
                            .requestBody("kamelet:setBody/test", "", String.class))
                    .isEqualTo("from-component-route");

            assertThat(
                    main.getCamelTemplate()
                            .requestBody("kamelet:setBody", "", String.class))
                    .isEqualTo("from-component-template");
            // uri properties have precedence over component properties.
            assertThat(
                    main.getCamelTemplate()
                            .requestBody("kamelet:setBody?bodyValue={{bodyValue}}", "", String.class))
                    .isEqualTo("from-uri");
        } catch (Exception e) {
            fail(e);
        } finally {
            main.stop();
        }
    }

    protected Properties getTestProperties() {
        return asProperties(
                "proxy.usr", "u+sr",
                "proxy.pwd", "p+wd",
                "raw.proxy.usr", "RAW(u+sr)",
                "raw.proxy.pwd", "RAW(p+wd)",
                "bodyValue", "from-uri",
                // component properties have precedence over global properties
                KAMELET_COMPONENT_PROPERTY_PREFIX + "templateProperties[setBody].bodyValue", "from-component-template",
                KAMELET_COMPONENT_PROPERTY_PREFIX + "routeProperties[test].bodyValue", "from-component-route",
                Kamelet.PROPERTIES_PREFIX + "setBody.bodyValue", "from-template",
                Kamelet.PROPERTIES_PREFIX + "setBody.test.bodyValue", "from-route");
    }

    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // template
                routeTemplate("setBody")
                        .templateParameter("bodyValue")
                        .from("kamelet:source")
                        .setBody().constant("{{bodyValue}}");

                // routes
                from("direct:someId").to("kamelet:setBody/someId");

                from("direct:test").to("kamelet:setBody");
            }
        };
    }
}
