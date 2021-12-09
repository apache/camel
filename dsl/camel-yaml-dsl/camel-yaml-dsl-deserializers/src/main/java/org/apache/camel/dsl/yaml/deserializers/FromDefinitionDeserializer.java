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

import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlSupport;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;

@YamlType(
          inline = true,
          types = FromDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "uri", type = "string", required = true),
                  @YamlProperty(name = "parameters", type = "object"),
                  @YamlProperty(name = "steps", type = "array:org.apache.camel.model.ProcessorDefinition", required = true)
          })
public class FromDefinitionDeserializer implements ConstructNode {

    @Override
    public Object construct(Node node) {

        // from must be wrapped in a route, so use existing route or create a new route
        RouteDefinition route = (RouteDefinition) node.getProperty(RouteDefinition.class.getName());
        if (route == null) {
            route = new RouteDefinition();
        }

        String uri = YamlSupport.creteEndpointUri(node, EndpointConsumerDeserializersResolver::resolveEndpointUri, route);
        if (uri == null) {
            throw new IllegalStateException("The endpoint URI must be set");
        }

        FromDefinition answer = new FromDefinition(uri);
        route.setInput(answer);
        return answer;
    }
}
