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
package org.apache.camel.dsl.yaml.common;

import java.util.Locale;

import org.apache.camel.util.StringHelper;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;

public abstract class YamlDeserializerBase<T> extends YamlDeserializerSupport implements ConstructNode {
    private final Class<T> type;

    public YamlDeserializerBase(Class<T> type) {
        this.type = type;
    }

    public Class<T> getType() {
        return type;
    }

    @Override
    public Object construct(Node node) {
        final T target;

        if (node.getNodeType() == NodeType.SCALAR) {
            ScalarNode mn = (ScalarNode) node;
            target = newInstance(mn.getValue());
        } else if (node.getNodeType() == NodeType.MAPPING) {
            MappingNode mn = (MappingNode) node;
            target = newInstance();

            setProperties(target, mn);
        } else {
            throw new IllegalArgumentException("Unsupported node type: " + node);
        }

        return target;
    }

    /**
     * Creates a Java instance of the expected type.
     *
     * @return the instance.
     */
    protected abstract T newInstance();

    /**
     * Creates a Java instance of the expected type from a string.
     *
     * @return the instance.
     */
    protected T newInstance(String value) {
        throw new IllegalArgumentException("Unsupported " + value);
    }

    /**
     * Set a property to the given target.
     *
     * @param target       the target object
     * @param propertyKey  the normalized property key
     * @param propertyName the name of the property as defined in the YAML
     * @param value        the value of the property as {@link Node}
     */
    protected boolean setProperty(T target, String propertyKey, String propertyName, Node value) {
        return false;
    }

    /**
     * Set properties from a YAML node to the given target.
     *
     * @param node   the node
     * @param target the target object
     */
    protected void setProperties(T target, MappingNode node) {
        org.apache.camel.dsl.yaml.common.YamlDeserializationContext dc = getDeserializationContext(node);

        for (NodeTuple tuple : node.getValue()) {
            final ScalarNode key = (ScalarNode) tuple.getKeyNode();
            final String propertyName = StringHelper.camelCaseToDash(key.getValue()).toLowerCase(Locale.US);
            final Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            if (!setProperty(target, propertyName, key.getValue(), val)) {
                handleUnknownProperty(target, propertyName, key.getValue(), val);
            }
        }
    }

    protected void handleUnknownProperty(T target, String propertyKey, String propertyName, Node value) {
        throw new IllegalArgumentException("Unsupported field: " + propertyName + " on " + target.getClass().getName());
    }
}
