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
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.JtaTransactionErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
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
          inline = false,
          nodes = { "error-handler", "errorHandler" },
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "deadLetterChannel",
                                type = "object:org.apache.camel.model.errorhandler.DeadLetterChannelDefinition",
                                oneOf = "errorHandler"),
                  @YamlProperty(name = "defaultErrorHandler",
                                type = "object:org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition",
                                oneOf = "errorHandler"),
                  @YamlProperty(name = "jtaTransactionErrorHandler",
                                type = "object:org.apache.camel.model.errorhandler.JtaTransactionErrorHandlerDefinition",
                                oneOf = "errorHandler"),
                  @YamlProperty(name = "noErrorHandler",
                                type = "object:org.apache.camel.model.errorhandler.NoErrorHandlerDefinition",
                                oneOf = "errorHandler"),
                  @YamlProperty(name = "refErrorHandler",
                                type = "object:org.apache.camel.model.errorhandler.RefErrorHandlerDefinition",
                                oneOf = "errorHandler"),
                  @YamlProperty(name = "springTransactionErrorHandler",
                                type = "object:org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition",
                                oneOf = "errorHandler"),
          })
public class ErrorHandlerBuilderDeserializer implements ConstructNode {

    private static CamelContextCustomizer customizer(ErrorHandlerFactory builder) {
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                camelContext.getCamelContextExtension().setErrorHandlerFactory(builder);
            }
        };
    }

    @Override
    public Object construct(Node node) {
        final MappingNode bn = asMappingNode(node);
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (NodeTuple tuple : bn.getValue()) {
            String key = asText(tuple.getKeyNode());
            Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            key = org.apache.camel.util.StringHelper.dashToCamelCase(key);
            switch (key) {
                case "deadLetterChannel":
                    return customizer(asType(val, DeadLetterChannelDefinition.class));
                case "defaultErrorHandler":
                    return customizer(asType(val, DefaultErrorHandlerDefinition.class));
                case "jtaTransactionErrorHandler":
                    return customizer(asType(val, JtaTransactionErrorHandlerDefinition.class));
                case "noErrorHandler":
                    return customizer(asType(val, NoErrorHandlerDefinition.class));
                case "refErrorHandler":
                    return customizer(asType(val, RefErrorHandlerDefinition.class));
                case "springTransactionErrorHandler":
                    return customizer(asType(val, JtaTransactionErrorHandlerDefinition.class));
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        throw new YamlDeserializationException(node, "Unable to determine the error handler type for the node");
    }
}
