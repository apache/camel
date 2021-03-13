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
package org.apache.camel.dsl.yaml.deserializers;

import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.snakeyaml.engine.v2.api.ConstructNode;

public class CustomResolver implements YamlDeserializerResolver {
    @Override
    public int getOrder() {
        return YamlDeserializerResolver.ORDER_DEFAULT;
    }

    @Override
    public ConstructNode resolve(String id) {
        switch (id) {
            //
            // Route
            //
            case "from":
                return new RouteFromDefinitionDeserializer();
            case "org.apache.camel.model.FromDefinition":
                return new FromDefinitionDeserializer();
            case "route":
            case "org.apache.camel.model.RouteDefinition":
                return new RouteDefinitionDeserializer();
            //
            // Expression
            //
            case "expression":
            case "org.apache.camel.model.language.ExpressionDefinition":
                return new ExpressionDeserializers.ExpressionDefinitionDeserializers();
            case "org.apache.camel.model.ExpressionSubElementDefinition":
                return new ExpressionDeserializers.ExpressionSubElementDefinitionDeserializers();

            //
            // Misc
            //
            case "beans":
                return new BeansDeserializer();
            case "error-handler":
                return new ErrorHandlerBuilderDeserializer();
            //case "do-try":
            //    return new TryDefinitionDeserializer();
            case "org.apache.camel.model.ProcessorDefinition":
                return new ProcessorDefinitionDeserializer();
            default:
                return null;
        }
    }
}
