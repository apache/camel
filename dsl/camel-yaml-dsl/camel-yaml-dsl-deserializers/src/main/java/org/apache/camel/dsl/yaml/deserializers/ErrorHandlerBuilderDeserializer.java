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

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.ErrorHandlerRefDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asType;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.getDeserializationContext;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

@YamlIn
@YamlType(
          nodes = { "error-handler", "errorHandler" },
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "ref", type = "string"),
                  @YamlProperty(name = "none", type = "object:org.apache.camel.model.errorhandler.NoErrorHandlerDefinition"),
                  @YamlProperty(name = "log",
                                type = "object:org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition"),
                  @YamlProperty(name = "dead-letter-channel",
                                type = "object:org.apache.camel.model.errorhandler.DeadLetterChannelDefinition"),
          })
public class ErrorHandlerBuilderDeserializer implements ConstructNode {

    private static CamelContextCustomizer customizer(ErrorHandlerFactory builder) {
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                camelContext.adapt(ExtendedCamelContext.class).setErrorHandlerFactory(builder);
            }
        };
    }

    @Override
    public Object construct(Node node) {
        final MappingNode bn = asMappingNode(node);
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (NodeTuple tuple : bn.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            switch (key) {
                case "ref":
                    // special for ref error handler, as it can be manually inlined
                    ErrorHandlerRefDefinition def = new ErrorHandlerRefDefinition(asText(val));
                    return customizer(def);
                case "none":
                    return customizer(asType(val, NoErrorHandlerDefinition.class));
                case "deadLetterChannel":
                case "dead-letter-channel":
                    return customizer(asType(val, DeadLetterChannelDefinition.class));
                case "log":
                    return customizer(asType(val, DefaultErrorHandlerDefinition.class));
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        throw new YamlDeserializationException(node, "Unable to determine the error handler type for the node");
    }
}
