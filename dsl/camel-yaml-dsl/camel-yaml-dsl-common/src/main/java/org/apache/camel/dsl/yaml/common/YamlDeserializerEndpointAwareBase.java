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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.StringHelper;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;

public abstract class YamlDeserializerEndpointAwareBase<T> extends YamlDeserializerBase<T> {

    public YamlDeserializerEndpointAwareBase(Class<T> type) {
        super(type);
    }

    /**
     * Set properties from a YAML node to the given target.
     *
     * @param node   the node
     * @param target the target object
     */
    @Override
    protected void setProperties(T target, MappingNode node) {
        YamlDeserializationContext dc = getDeserializationContext(node);

        Map<String, Object> parameters = null;

        for (NodeTuple tuple : node.getValue()) {
            final ScalarNode key = (ScalarNode) tuple.getKeyNode();
            final String propertyName = StringHelper.camelCaseToDash(key.getValue()).toLowerCase(Locale.US);
            final Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            switch (propertyName) {
                case "parameters":
                    parameters = asScalarMap(tuple.getValueNode());
                    break;
                default:
                    if (!setProperty(target, propertyName, key.getValue(), val)) {
                        handleUnknownProperty(target, propertyName, key.getValue(), val);
                    }
            }
        }

        if (parameters != null) {
            setEndpointUri(dc.getCamelContext(), node, target, parameters);
        }
    }

    protected abstract void setEndpointUri(CamelContext context, Node node, T target, Map<String, Object> parameters);
}
