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

import java.util.List;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Converts Java model classes to OpenAPI schemas used by the REST DSL OpenAPI document generator.
 *
 * @since 4.24.0
 */
public interface RestModelConverters {

    /**
     * Adds a Swagger model converter to the conversion chain.
     *
     * @param converter the converter to add
     * @since           4.24.0
     */
    void addConverter(ModelConverter converter);

    /**
     * Excludes classes in the given package (and its subpackages) from model conversion.
     *
     * @param pkg the package name to skip
     * @since     4.24.0
     */
    void addPackageToSkip(String pkg);

    /**
     * Excludes the given class from model conversion.
     *
     * @param cls the fully-qualified class name to skip
     * @since     4.24.0
     */
    void addClassToSkip(String cls);

    /**
     * Reads the schema(s) for the given class.
     *
     * @param  oasDocument the OpenAPI document being generated
     * @param  clazz       the model class
     * @return             the generated schemas, or {@code null} when the class does not need a schema
     * @since              4.24.0
     */
    List<? extends Schema<?>> readClass(OpenAPI oasDocument, Class<?> clazz);
}
