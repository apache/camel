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
package org.apache.camel.generator.openapi;

import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestsDefinition;

public final class RestDslDefinitionGenerator extends RestDslGenerator<RestDslDefinitionGenerator> {

    RestDslDefinitionGenerator(final OasDocument document) {
        super(document);
    }

    public RestsDefinition generate(final CamelContext context) {
        final RestDefinitionEmitter emitter = new RestDefinitionEmitter(context);
        final String basePath = RestDslGenerator.determineBasePathFrom(document);
        final PathVisitor<RestsDefinition> restDslStatement
                = new PathVisitor<>(basePath, emitter, filter, destinationGenerator());

        document.paths.getPathItems().forEach(restDslStatement::visit);

        return emitter.result();
    }

}
