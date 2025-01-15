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

import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenApiUtilsTest {

    private static final String TAG_OPENAPI_YAML = """
            ---
            openapi: <openapi_version>
            info:
                title: Tag API
                version: v1
            paths:
              /tag:
                get:
                  summary: Get a tag
                  operationId: getTag
                  responses:
                    '200':
                      description: Successful operation
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/TagResponseDto'
            components:
              schemas:
                TagResponseDto:
                  type: object
                  properties:
                    id:
                      type: integer
                      description: The ID of the tag
                    name:
                      type: string
                      description: The name of the tag
                  required:
                    - id
                    - name
                    """;

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

    @Test
    public void shouldReturnCorrectRequestClassNameForSchemaName() {
        //When the class name is provided in the schema name
        String schemaName = "Tag";
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();

        Operation operation = new Operation();
        Schema<Object> tagSchema = createTagSchema();
        RequestBody requestBody = createRequestBody(tagSchema);
        operation.requestBody(requestBody);

        Components components = new Components();
        components.addSchemas(schemaName, tagSchema);

        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, components);
        assertEquals(Tag.class.getName(), utils.manageRequestBody(operation));
    }

    @Test
    public void shouldReturnCorrectRequestClassNameForSchemaTitle() {
        String schemaName = "TagSchema";
        //When the class name is provided in the schema title instead of schema name
        String schemaTitle = "TagRequestDto";
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();

        Operation operation = new Operation();
        Schema<Object> tagSchema = createTagSchema(schemaTitle);
        RequestBody requestBody = createRequestBody(tagSchema);
        operation.requestBody(requestBody);

        Components components = new Components();
        components.addSchemas(schemaName, tagSchema);

        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, components);
        assertEquals(TagRequestDto.class.getName(), utils.manageRequestBody(operation));
    }

    @Test
    public void shouldReturnCorrectResponseClassNameForSchemaName() {
        //When the class name is provided in the schema name
        String schemaName = "Tag";
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();
        Schema<Object> tagSchema = createTagSchema();

        Operation operation = new Operation();
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", createResponse(tagSchema));
        operation.setResponses(responses);

        Components components = new Components();
        components.addSchemas(schemaName, tagSchema);

        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, components);
        assertEquals(Tag.class.getName(), utils.manageResponseBody(operation));
    }

    @Test
    public void shouldReturnCorrectResponseClassNameForSchemaTitle() {
        String schemaName = "TagSchema";
        //When the class name is provided in the schema title instead of schema name
        String schemaTitle = "TagResponseDto";
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();

        Operation operation = new Operation();
        Schema<Object> tagSchema = createTagSchema(schemaTitle);
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", createResponse(tagSchema));
        operation.setResponses(responses);

        Components components = new Components();
        components.addSchemas(schemaName, tagSchema);

        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, components);
        assertEquals(TagResponseDto.class.getName(), utils.manageResponseBody(operation));
    }

    @Test
    public void shouldManageResponseFromOpenApi31Parser() throws Exception {
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        OpenAPI openApi = parser.readContents(TAG_OPENAPI_YAML.replace("<openapi_version>", "3.1.0")).getOpenAPI();
        Operation operation = openApi.getPaths().get("/tag").getGet();
        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, openApi.getComponents());
        assertEquals(TagResponseDto.class.getName() + "[]", utils.manageResponseBody(operation));
    }

    @Test
    public void shouldManageRequestFromOpenApi30Parser() throws Exception {
        String bindingPackagePath = OpenApiUtils.class.getPackage().getName();
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        OpenAPI openApi = parser.readContents(TAG_OPENAPI_YAML.replace("<openapi_version>", "3.0.0")).getOpenAPI();
        Operation operation = openApi.getPaths().get("/tag").getGet();
        OpenApiUtils utils = new OpenApiUtils(new DefaultCamelContext(), bindingPackagePath, openApi.getComponents());
        assertEquals(TagResponseDto.class.getName() + "[]", utils.manageResponseBody(operation));
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

    private ApiResponse createResponse(Schema<?> schema) {
        ApiResponse response = new ApiResponse();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        content.addMediaType("application/json", mediaType);
        response.setContent(content);

        return response;
    }

    private RequestBody createRequestBody(Schema<?> schema) {
        RequestBody requestBody = new RequestBody();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        return requestBody;
    }

    private static Schema<Object> createTagSchema() {
        Schema<Object> tagSchema = new ObjectSchema();
        Schema<Number> idSchema = new IntegerSchema();
        Schema<String> nameSchema = new StringSchema();
        tagSchema.setProperties(Map.of(
                "id", idSchema,
                "name", nameSchema));
        tagSchema.setDescription("Schema representing the Tag class");
        return tagSchema;
    }

    private static Schema<Object> createTagSchema(String title) {
        Schema<Object> tagSchema = createTagSchema();
        tagSchema.setTitle(title);
        return tagSchema;
    }
}
