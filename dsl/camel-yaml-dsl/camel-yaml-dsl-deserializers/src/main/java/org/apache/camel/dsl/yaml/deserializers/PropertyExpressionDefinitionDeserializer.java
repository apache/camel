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

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.model.PropertyExpressionDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.Node;

import static org.apache.camel.dsl.yaml.deserializers.ExpressionDeserializers.constructExpressionType;

@YamlType(
          nodes = "propertyExpression",
          types = org.apache.camel.model.PropertyExpressionDefinition.class,
          order = org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1,
          displayName = "Property Expression",
          description = "A key value pair where the value is an expression",
          deprecated = false,
          properties = {
                  @YamlProperty(name = "key", type = "string", required = true, description = "The name of the property",
                                displayName = "Key"),
                  @YamlProperty(name = "expression", required = true,
                                type = "object:org.apache.camel.model.language.ExpressionDefinition",
                                description = "The property value as an expression",
                                displayName = "Expression", oneOf = "expression")
          })
public class PropertyExpressionDefinitionDeserializer extends YamlDeserializerBase<PropertyExpressionDefinition> {

    public PropertyExpressionDefinitionDeserializer() {
        super(PropertyExpressionDefinition.class);
    }

    @Override
    protected PropertyExpressionDefinition newInstance() {
        return new PropertyExpressionDefinition();
    }

    @Override
    protected boolean setProperty(
            PropertyExpressionDefinition target, String propertyKey,
            String propertyName, Node node) {
        propertyKey = org.apache.camel.util.StringHelper.dashToCamelCase(propertyKey);
        switch (propertyKey) {
            case "key": {
                String val = asText(node);
                target.setKey(val);
                break;
            }
            default: {
                ExpressionDefinition exp = constructExpressionType(propertyKey, node);
                if (exp != null) {
                    target.setExpression(exp);
                    break;
                }
                // unknown
                return false;
            }
        }
        return true;
    }

}
