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
package org.apache.camel.dsl.yaml

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.Model
import org.apache.camel.model.transformer.EndpointTransformerDefinition
import org.apache.camel.model.transformer.CustomTransformerDefinition
import org.apache.camel.model.transformer.LoadTransformerDefinition

class TransformersTest extends YamlTestSupport {

    def "transformers with loadTransformer"() {
        when:
        loadRoutes """
                - transformers:
                    loadTransformer:
                      defaults: true
            """

        then:
        def transformers = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers()
        transformers.size() == 1
        with(transformers[0], LoadTransformerDefinition) {
            it.defaults == 'true'
        }
    }

    def "transformers with endpointTransformer"() {
        when:
        loadRoutes """
                - transformers:
                    endpointTransformer:
                      ref: myXmlEndpoint
                      fromType: xml:XmlXOrder
                      toType: "java:org.example.XOrder"
            """

        then:
        def transformers = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers()
        transformers.size() == 1
        with(transformers[0], EndpointTransformerDefinition) {
            it.ref == 'myXmlEndpoint'
            it.fromType == 'xml:XmlXOrder'
            it.toType == 'java:org.example.XOrder'
        }
    }

    def "transformers with customTransformer"() {
        when:
        loadRoutes """
                - transformers:
                    customTransformer:
                      className: org.example.MyTransformer
                      fromType: other:OtherXOrder
                      toType: "java:org.example.XOrder"
            """

        then:
        def transformers = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers()
        transformers.size() == 1
        with(transformers[0], CustomTransformerDefinition) {
            it.className == 'org.example.MyTransformer'
            it.fromType == 'other:OtherXOrder'
            it.toType == 'java:org.example.XOrder'
        }
    }

    def "multiple transformers"() {
        when:
        loadRoutes """
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
            """

        then:
        def transformers = context.getCamelContextExtension().getContextPlugin(Model.class).getTransformers()
        transformers.size() == 3
        transformers[0] instanceof LoadTransformerDefinition
        transformers[1] instanceof EndpointTransformerDefinition
        transformers[2] instanceof CustomTransformerDefinition
    }
}
