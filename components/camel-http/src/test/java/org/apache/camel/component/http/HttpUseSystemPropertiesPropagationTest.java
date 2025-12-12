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
package org.apache.camel.component.http;

import java.util.stream.Stream;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("require-isolated-jvm")
public class HttpUseSystemPropertiesPropagationTest extends CamelTestSupport {

    private static final String uri = "https://camel.apache.org/";

    private static Stream<Arguments> testCases() {
        return Stream.of(
                Arguments.of("No query parameter - component value false", uri, false, false),
                Arguments.of("No query parameter - component value true", uri, true, true),
                Arguments.of("?useSystemProperties=true - component value false", uri + "?useSystemProperties=true", false,
                        true),
                Arguments.of("?useSystemProperties=false - component value true", uri + "?useSystemProperties=false", true,
                        false),
                Arguments.of("?useSystemProperties=true - component value true", uri + "?useSystemProperties=true", true, true),
                Arguments.of("?useSystemProperties=false - component value false", uri + "?useSystemProperties=false", false,
                        false));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("testCases")
    public void testHttpUseSystemPropertiesPropagation(
            String testName, String camelURI, boolean componentValue, boolean expected)
            throws Exception {
        HttpComponent component = new HttpComponent();
        component.setCamelContext(context);
        component.setUseSystemProperties(componentValue);
        HttpEndpoint endpoint = (HttpEndpoint) component.createEndpoint(camelURI);
        Assertions.assertEquals(expected, endpoint.isUseSystemProperties());
    }
}
