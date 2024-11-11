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
package org.apache.camel.component.rest.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiUtilsTest {
    @Test
    public void shouldReturnAllProduces() {
        Operation operation = new Operation();

        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", createResponse("application/json", "application/xml"));
        responses.addApiResponse("400", createResponse("application/problem+json"));
        responses.addApiResponse("404", createResponse("application/problem+json"));
        operation.setResponses(responses);

        OpenApiUtils utils = new OpenApiUtils(null, null, null);
        assertThat(utils.getProduces(operation)).isEqualTo("application/json,application/problem+json,application/xml");
    }

    private ApiResponse createResponse(String... contentTypes) {
        ApiResponse response = new ApiResponse();

        Content content = new Content();
        for (String contentType : contentTypes) {
            content.addMediaType(contentType, new MediaType());
        }
        response.setContent(content);

        return response;
    }
}
