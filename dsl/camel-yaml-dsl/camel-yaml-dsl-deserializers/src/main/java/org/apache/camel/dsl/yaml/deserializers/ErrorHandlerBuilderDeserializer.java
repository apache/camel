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

import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.Node;

@YamlIn
@YamlType(
          nodes = "error-handler",
          types = ErrorHandlerBuilderRef.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "ref", type = "string")
          })
public class ErrorHandlerBuilderDeserializer extends YamlDeserializerBase<ErrorHandlerBuilderRef> {
    public ErrorHandlerBuilderDeserializer() {
        super(ErrorHandlerBuilderRef.class);
    }

    @Override
    protected ErrorHandlerBuilderRef newInstance() {
        return new ErrorHandlerBuilderRef();
    }

    @Override
    protected ErrorHandlerBuilderRef newInstance(String value) {
        return new ErrorHandlerBuilderRef(value);
    }

    @Override
    protected boolean setProperty(ErrorHandlerBuilderRef target, String propertyKey, String propertyName, Node value) {
        if ("ref".equals(propertyKey)) {
            target.setRef(asText(value));
            return true;
        }

        return false;
    }
}
