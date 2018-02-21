/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.generator.swagger;

import io.swagger.models.Swagger;
import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.rest.RestsDefinition;

public class RestDslXmlGenerator extends RestDslGenerator<RestDslXmlGenerator> {

    // TODO: remove root namespace
    // TODO: re-order attributes so id/url is first

    RestDslXmlGenerator(final Swagger swagger) {
        super(swagger);
    }

    public String generate(final CamelContext context) throws Exception {
        final RestDefinitionEmitter emitter = new RestDefinitionEmitter(context);

        final PathVisitor<RestsDefinition> restDslStatement = new PathVisitor<>(emitter, destinationGenerator());

        swagger.getPaths().forEach(restDslStatement::visit);

        RestsDefinition rests = emitter.result();
        String xml = ModelHelper.dumpModelAsXml(context, rests);
        return xml;
    }
}
