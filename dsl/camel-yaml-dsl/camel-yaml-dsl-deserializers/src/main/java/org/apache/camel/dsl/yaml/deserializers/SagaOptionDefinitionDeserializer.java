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
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.SagaOptionDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.Node;

@YamlType(
          types = SagaOptionDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "__extends", type = "ref:org.apache.camel.model.language.ExpressionDefinition"),
                  @YamlProperty(name = "option-name", type = "string", required = true)
          })
public class SagaOptionDefinitionDeserializer extends YamlDeserializerBase<SagaOptionDefinition> {
    public SagaOptionDefinitionDeserializer() {
        super(SagaOptionDefinition.class);
    }

    @Override
    protected SagaOptionDefinition newInstance() {
        return new SagaOptionDefinition();
    }

    @Override
    protected boolean setProperty(SagaOptionDefinition target, String propertyKey, String propertyName, Node value) {
        switch (propertyKey) {
            case "option-name":
                target.setOptionName(asText(value));
                break;
            case "expression":
                ExpressionDefinition ed = ExpressionDeserializers.constructExpressionType(value);
                if (ed != null) {
                    target.setExpression(ed);
                }
                break;
            default:
                return false;
        }

        return true;
    }

    @Override
    protected void handleUnknownProperty(SagaOptionDefinition target, String propertyKey, String propertyName, Node node) {
        ExpressionDefinition ed = ExpressionDeserializers.constructExpressionType(propertyKey, node);
        if (ed != null) {
            target.setExpression(ed);
        } else {
            super.handleUnknownProperty(target, propertyKey, propertyName, node);
        }
    }
}
