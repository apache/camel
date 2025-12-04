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

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asType;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.getDeserializationContext;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.ErrorHandlerDefinition;
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

@YamlIn
@YamlType(
        inline = false,
        nodes = {"errorHandler"},
        order = YamlDeserializerResolver.ORDER_DEFAULT,
        properties = {
            @YamlProperty(name = "id", type = "string", description = "The id of this node", displayName = "Id"),
            @YamlProperty(
                    name = "deadLetterChannel",
                    type = "object:org.apache.camel.model.errorhandler.DeadLetterChannelDefinition",
                    oneOf = "errorHandler"),
            @YamlProperty(
                    name = "defaultErrorHandler",
                    type = "object:org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition",
                    oneOf = "errorHandler"),
            @YamlProperty(
                    name = "jtaTransactionErrorHandler",
                    type = "object:org.apache.camel.model.errorhandler.JtaTransactionErrorHandlerDefinition",
                    oneOf = "errorHandler"),
            @YamlProperty(
                    name = "noErrorHandler",
                    type = "object:org.apache.camel.model.errorhandler.NoErrorHandlerDefinition",
                    oneOf = "errorHandler"),
            @YamlProperty(
                    name = "refErrorHandler",
                    type = "object:org.apache.camel.model.errorhandler.RefErrorHandlerDefinition",
                    oneOf = "errorHandler"),
            @YamlProperty(
                    name = "springTransactionErrorHandler",
                    type = "object:org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition",
                    oneOf = "errorHandler"),
        })
public class ErrorHandlerDeserializer implements ConstructNode {

    private final boolean global;

    public ErrorHandlerDeserializer() {
        this(false);
    }

    public ErrorHandlerDeserializer(boolean global) {
        this.global = global;
    }

    private static CamelContextCustomizer customizer(ErrorHandlerDefinition builder) {
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                camelContext.getCamelContextExtension().setErrorHandlerFactory(builder.getErrorHandlerType());
            }
        };
    }

    @Override
    public Object construct(Node node) {
        final MappingNode bn = asMappingNode(node);
        final YamlDeserializationContext dc = getDeserializationContext(node);

        ErrorHandlerFactory factory = null;
        for (NodeTuple tuple : bn.getValue()) {
            String key = asText(tuple.getKeyNode());
            Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            switch (key) {
                case "deadLetterChannel":
                    factory = asType(val, DeadLetterChannelDefinition.class);
                    break;
                case "defaultErrorHandler":
                    factory = asType(val, DefaultErrorHandlerDefinition.class);
                    break;
                case "jtaTransactionErrorHandler":
                case "springTransactionErrorHandler":
                    factory = asType(val, JtaTransactionErrorHandlerDefinition.class);
                    break;
                case "noErrorHandler":
                    factory = asType(val, NoErrorHandlerDefinition.class);
                    break;
                case "refErrorHandler":
                    factory = asType(val, RefErrorHandlerDefinition.class);
                    break;
                case "id": {
                    // not in use
                    break;
                }
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        if (factory == null) {
            throw new YamlDeserializationException(node, "Unable to determine the error handler type for the node");
        }

        // wrap in model
        ErrorHandlerDefinition answer = new ErrorHandlerDefinition();
        answer.setErrorHandlerType(factory);

        if (global) {
            // global scoped should register factory on camel context via customizer
            return customizer(answer);
        }
        return answer;
    }
}
