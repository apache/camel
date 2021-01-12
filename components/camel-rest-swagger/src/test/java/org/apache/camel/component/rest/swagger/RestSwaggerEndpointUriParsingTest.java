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
package org.apache.camel.component.rest.swagger;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class RestSwaggerEndpointUriParsingTest {

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldParseEndpointUri(String uri, String remaining, String specificationUri, String operationId) {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint(
                specificationUri, remaining, component,
                Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri().toString()).isEqualTo(specificationUri);
        assertThat(endpoint.getOperationId()).isEqualTo(operationId);
    }

    public static Iterable<Object[]> parameters() {
        return Arrays.asList(
                params("rest-swagger:operation", "operation", "swagger.json", "operation"),
                params("rest-swagger:my-api.json#operation", "my-api.json#operation", "my-api.json", "operation"),
                params("rest-swagger:http://api.example.com/swagger.json#operation",
                        "http://api.example.com/swagger.json#operation", "http://api.example.com/swagger.json", "operation"));
    }

    static Object[] params(final String uri, final String remaining, final String specificationUri, final String operationId) {
        return new Object[] { uri, remaining, specificationUri, operationId };
    }
}
