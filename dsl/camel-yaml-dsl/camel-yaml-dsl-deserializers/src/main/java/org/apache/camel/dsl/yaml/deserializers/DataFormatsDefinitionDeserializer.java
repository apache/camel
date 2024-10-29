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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlIn
@YamlType(
          nodes = "dataFormats",
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "__extends",
                                type = "array:org.apache.camel.model.dataformat.DataFormatsDefinition")
          })
public class DataFormatsDefinitionDeserializer extends YamlDeserializerSupport implements ConstructNode {

    @Override
    public Object construct(Node node) {
        final DataFormatsCustomizer customizer = new DataFormatsCustomizer();

        final YamlDeserializationContext dc = getDeserializationContext(node);
        final YamlDeserializationContext resolver
                = (YamlDeserializationContext) node.getProperty(YamlDeserializationContext.class.getName());
        if (resolver == null) {
            throw new YamlDeserializationException(node, "Unable to find YamlConstructor");
        }

        final SequenceNode sn = asSequenceNode(node);
        for (Node item : sn.getValue()) {
            setDeserializationContext(item, dc);
            MappingNode mn = asMappingNode(item);
            for (NodeTuple nt : mn.getValue()) {
                String name = asText(nt.getKeyNode());
                ConstructNode cn = resolver.resolve(mn, name);
                Object answer = cn.construct(nt.getValueNode());
                if (answer instanceof DataFormatDefinition def) {
                    if (dc != null) {
                        def.setResource(dc.getResource());
                    }
                    customizer.addDataFormat(def);
                }
            }
        }

        return customizer;
    }

    private static class DataFormatsCustomizer implements CamelContextCustomizer {

        private final List<DataFormatDefinition> list = new ArrayList<>();

        void addDataFormat(DataFormatDefinition def) {
            list.add(def);
        }

        @Override
        public void configure(CamelContext camelContext) {
            if (!list.isEmpty()) {
                DataFormatsDefinition def = new DataFormatsDefinition();
                def.setDataFormats(list);

                // register data formats to model
                Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
                model.getDataFormats().putAll(def.asMap());
            }
        }
    }

}
