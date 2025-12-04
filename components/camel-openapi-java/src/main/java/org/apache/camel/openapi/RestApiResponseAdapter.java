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

package org.apache.camel.openapi;

import java.io.IOException;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.Exchange;

/**
 * Adapter for rendering API response
 */
public interface RestApiResponseAdapter {

    /**
     * Sets the generated OpenAPI model
     */
    void setOpenApi(OpenAPI openApi);

    /**
     * Gets the generated OpenAPI model
     */
    OpenAPI getOpenApi();

    /**
     * Adds a header
     */
    void setHeader(String name, String value);

    /**
     * The content of the OpenAPI spec as byte array
     */
    void writeBytes(byte[] bytes) throws IOException;

    /**
     * There is no Rest DSL and therefore no OpenAPI spec
     */
    void noContent();

    /**
     * Copy content from this adapter into the given {@link Exchange}.
     */
    void copyResult(Exchange exchange);
}
