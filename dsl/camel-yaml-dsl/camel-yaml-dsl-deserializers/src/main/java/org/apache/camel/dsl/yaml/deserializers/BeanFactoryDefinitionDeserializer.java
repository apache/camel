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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.model.BeanFactoryDefinition;
import org.snakeyaml.engine.v2.nodes.Node;

/**
 * The base class for the YAML deserializers of bean factories.
 *
 * @param <T> the type of nodes that define a bean factory
 */
public abstract class BeanFactoryDefinitionDeserializer<T extends BeanFactoryDefinition<?>> extends YamlDeserializerBase<T> {

    protected BeanFactoryDefinitionDeserializer(Class<T> clazz) {
        super(clazz);
    }

    @Override
    protected boolean setProperty(
            T target, String propertyKey,
            String propertyName, Node node) {
        propertyKey = org.apache.camel.util.StringHelper.dashToCamelCase(propertyKey);
        switch (propertyKey) {
            case "name": {
                String val = asText(node);
                target.setName(val);
                break;
            }
            case "constructors": {
                target.setConstructors(asConstructorMap(asMap(node)));
                break;
            }
            case "properties": {
                target.setProperties(asMap(node));
                break;
            }
            case "initMethod": {
                String val = asText(node);
                target.setInitMethod(val);
                break;
            }
            case "destroyMethod": {
                String val = asText(node);
                target.setDestroyMethod(val);
                break;
            }
            case "factoryBean": {
                String val = asText(node);
                target.setFactoryBean(val);
                break;
            }
            case "factoryMethod": {
                String val = asText(node);
                target.setFactoryMethod(val);
                break;
            }
            case "builderClass": {
                String val = asText(node);
                target.setBuilderClass(val);
                break;
            }
            case "builderMethod": {
                String val = asText(node);
                target.setBuilderMethod(val);
                break;
            }
            case "scriptLanguage": {
                String val = asText(node);
                target.setScriptLanguage(val);
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

    private static Map<Integer, Object> asConstructorMap(Map<String, Object> map) {
        Map<Integer, Object> answer = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            answer.put(Integer.valueOf(k), v);
        });
        return answer;
    }
}
