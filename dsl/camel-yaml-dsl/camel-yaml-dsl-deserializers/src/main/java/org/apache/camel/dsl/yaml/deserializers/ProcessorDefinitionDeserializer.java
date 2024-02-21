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

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.getDeserializationContext;

@YamlType(
          types = ProcessorDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT)
public class ProcessorDefinitionDeserializer implements ConstructNode {

    @Override
    public Object construct(Node node) {
        final YamlDeserializationContext dc = getDeserializationContext(node);
        final ConstructNode constructor = dc.mandatoryResolve(node);
        final Object result = constructor.construct(node);

        if (result != null && !(result instanceof ProcessorDefinition<?>)) {
            throw new IllegalArgumentException("Unexpected type: " + result.getClass());
        }

        return result;
    }
}
