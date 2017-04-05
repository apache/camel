/**
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
package org.apache.camel.generator.swagger;

import java.nio.file.Path;

import javax.annotation.processing.Filer;

import io.swagger.models.Swagger;

import org.apache.camel.model.rest.RestsDefinition;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Source code and {@link RestsDefinition} generator that generates Camel REST
 * DSL implementations from Swagger (OpenAPI) specifications.
 */
public abstract class RestDslGenerator<G> {

    final Swagger swagger;

    private DestinationGenerator destinationGenerator = new DirectToOperationId();

    RestDslGenerator(final Swagger swagger) {
        this.swagger = notNull(swagger, "swagger");
    }

    public G withDestinationGenerator(final DestinationGenerator directRouteGenerator) {
        notNull(directRouteGenerator, "directRouteGenerator");
        this.destinationGenerator = directRouteGenerator;

        @SuppressWarnings("unchecked")
        final G that = (G) this;

        return that;
    }

    DestinationGenerator destinationGenerator() {
        return destinationGenerator;
    }

    public static RestDslSourceCodeGenerator<Appendable> toAppendable(final Swagger swagger) {
        return new AppendableGenerator(swagger);
    }

    public static RestDslDefinitionGenerator toDefinition(final Swagger swagger) {
        return new RestDslDefinitionGenerator(swagger);
    }

    public static RestDslSourceCodeGenerator<Filer> toFiler(final Swagger swagger) {
        return new FilerGenerator(swagger);
    }

    public static RestDslSourceCodeGenerator<Path> toPath(final Swagger swagger) {
        return new PathGenerator(swagger);
    }
}
