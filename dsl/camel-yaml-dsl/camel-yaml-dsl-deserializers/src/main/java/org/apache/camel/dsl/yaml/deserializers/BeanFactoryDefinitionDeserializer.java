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
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.snakeyaml.engine.v2.nodes.Node;

/**
 * The base class for the YAML deserializers of bean factories.
 *
 * @param <T> the type of nodes that define a bean factory
 */
public abstract class BeanFactoryDefinitionDeserializer<T extends BeanFactoryDefinition<?, ?>> extends YamlDeserializerBase<T> {
    protected BeanFactoryDefinitionDeserializer(Class<T> clazz) {
        super(clazz);
    }

    @Override
    protected boolean setProperty(
            T target, String propertyKey,
            String propertyName, Node node) {
        switch (propertyKey) {
            case "beanType":
            case "bean-type": {
                String val = asText(node);
                target.setBeanType(val);
                break;
            }
            case "name": {
                String val = asText(node);
                target.setName(val);
                break;
            }
            case "property": {
                java.util.List<PropertyDefinition> val
                        = asFlatList(node, PropertyDefinition.class);
                target.setPropertyDefinitions(val);
                break;
            }
            case "properties": {
                target.setProperties(asMap(node));
                break;
            }
            case "script": {
                String val = asText(node);
                target.setScript(val);
                break;
            }
            case "type": {
                String val = asText(node);
                target.setType(val);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }
}
