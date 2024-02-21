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
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.ConstructNode;

public class CustomResolver implements YamlDeserializerResolver {
    public static final Logger LOG = LoggerFactory.getLogger(CustomResolver.class);

    @Override
    public int getOrder() {
        return YamlDeserializerResolver.ORDER_DEFAULT;
    }

    private final BeansDeserializer beansDeserializer;

    public CustomResolver(BeansDeserializer beansDeserializer) {
        this.beansDeserializer = beansDeserializer;
    }

    @Override
    public ConstructNode resolve(String id) {
        if (id != null && id.contains("-")) {
            LOG.warn(
                    "The kebab-case '{}' is deprecated and it will be removed in the next version. Use the camelCase '{}' instead.",
                    id, StringHelper.dashToCamelCase(id));
        }

        id = org.apache.camel.util.StringHelper.dashToCamelCase(id);
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
            case "routeConfiguration":
            case "org.apache.camel.model.RouteConfigurationDefinition":
                return new RouteConfigurationDefinitionDeserializer();
            case "routeTemplate":
            case "org.apache.camel.model.RouteTemplateDefinition":
                return new RouteTemplateDefinitionDeserializer();
            case "templatedRoute":
            case "org.apache.camel.model.TemplatedRouteDefinition":
                return new TemplatedRouteDefinitionDeserializer();
            case "org.apache.camel.model.RouteTemplateBeanDefinition":
                return new RouteTemplateBeanDefinitionDeserializer();
            case "org.apache.camel.model.TemplatedRouteBeanDefinition":
                return new TemplatedRouteBeanDefinitionDeserializer();
            case "org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition":
                return new OutputAwareFromDefinitionDeserializer();

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
                return beansDeserializer;
            case "errorHandler":
                return new ErrorHandlerBuilderDeserializer();
            case "org.apache.camel.model.ProcessorDefinition":
                return new ProcessorDefinitionDeserializer();
            case "kamelet":
            case "org.apache.camel.model.KameletDefinition":
                return new KameletDeserializer();
            default:
                return null;
        }
    }
}
