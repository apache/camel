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
package org.apache.camel.dsl.yaml;

import java.util.List;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.Model;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.LoadTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransformersTest extends YamlTestSupport {

    @Test
    void transformersWithLoadTransformer() throws Exception {
        loadRoutes("""
                - transformers:
                    loadTransformer:
                      defaults: true
                """);

        List<TransformerDefinition> transformers
                = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers();
        assertThat(transformers.size()).isEqualTo(1);

        assertThat(transformers.get(0)).isInstanceOf(LoadTransformerDefinition.class);
        LoadTransformerDefinition lt = (LoadTransformerDefinition) transformers.get(0);
        assertThat(lt.getDefaults()).isEqualTo("true");
    }

    @Test
    void transformersWithEndpointTransformer() throws Exception {
        loadRoutes("""
                - transformers:
                    endpointTransformer:
                      ref: myXmlEndpoint
                      fromType: xml:XmlXOrder
                      toType: "java:org.example.XOrder"
                """);

        List<TransformerDefinition> transformers
                = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers();
        assertThat(transformers.size()).isEqualTo(1);

        assertThat(transformers.get(0)).isInstanceOf(EndpointTransformerDefinition.class);
        EndpointTransformerDefinition et = (EndpointTransformerDefinition) transformers.get(0);
        assertThat(et.getRef()).isEqualTo("myXmlEndpoint");
        assertThat(et.getFromType()).isEqualTo("xml:XmlXOrder");
        assertThat(et.getToType()).isEqualTo("java:org.example.XOrder");
    }

    @Test
    void transformersWithCustomTransformer() throws Exception {
        loadRoutes("""
                - transformers:
                    customTransformer:
                      className: org.example.MyTransformer
                      fromType: other:OtherXOrder
                      toType: "java:org.example.XOrder"
                """);

        List<TransformerDefinition> transformers
                = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers();
        assertThat(transformers.size()).isEqualTo(1);

        assertThat(transformers.get(0)).isInstanceOf(CustomTransformerDefinition.class);
        CustomTransformerDefinition ct = (CustomTransformerDefinition) transformers.get(0);
        assertThat(ct.getClassName()).isEqualTo("org.example.MyTransformer");
        assertThat(ct.getFromType()).isEqualTo("other:OtherXOrder");
        assertThat(ct.getToType()).isEqualTo("java:org.example.XOrder");
    }

    @Test
    void transformersWithDataFormatTransformer() throws Exception {
        loadRoutes("""
                - transformers:
                    dataFormatTransformer:
                      fromType: xml:XmlXOrder
                      toType: "java:org.example.XOrder"
                      json:
                        library: Jackson
                """);

        List<TransformerDefinition> transformers
                = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers();
        assertThat(transformers.size()).isEqualTo(1);

        assertThat(transformers.get(0)).isInstanceOf(DataFormatTransformerDefinition.class);
        DataFormatTransformerDefinition dt = (DataFormatTransformerDefinition) transformers.get(0);
        assertThat(dt.getFromType()).isEqualTo("xml:XmlXOrder");
        assertThat(dt.getToType()).isEqualTo("java:org.example.XOrder");
        assertThat(dt.getDataFormatType()).isNotNull();
    }

    @Test
    void multipleTransformers() throws Exception {
        loadRoutes("""
                - transformers:
                    loadTransformer:
                      defaults: true
                    endpointTransformer:
                      ref: myXmlEndpoint
                      fromType: xml:XmlXOrder
                      toType: "java:org.example.XOrder"
                    customTransformer:
                      className: org.example.MyTransformer
                      fromType: other:OtherXOrder
                      toType: "java:org.example.XOrder"
                """);

        List<TransformerDefinition> transformers
                = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers();
        assertThat(transformers.size()).isEqualTo(3);
        assertThat(transformers.get(0)).isInstanceOf(LoadTransformerDefinition.class);
        assertThat(transformers.get(1)).isInstanceOf(EndpointTransformerDefinition.class);
        assertThat(transformers.get(2)).isInstanceOf(CustomTransformerDefinition.class);
    }
}
