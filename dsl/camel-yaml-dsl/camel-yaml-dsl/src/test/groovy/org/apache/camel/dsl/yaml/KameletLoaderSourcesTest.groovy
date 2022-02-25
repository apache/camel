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

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions

class KameletLoaderSourcesTest extends YamlTestSupport {

    def "integration sources"() {
        when:
        loadIntegrations('''
            apiVersion: camel.apache.org/v1
            kind: Integration
            metadata:
              name: myapp
            spec:
              flows:
                - from:
                    uri: "timer:demo"
                    parameters:
                      period: 3000
                    steps:
                      - process: 
                          ref: myProcessor  
                      - log: "${body} + ${headers}"
              sources:
                - content: |-
                    beans {
                        myProcessor = processor { 
                            it.in.body = 'Hello Camel K!'
                        }
                    }
                  name: myapp.groovy
        ''')
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0]) {
            input.endpointUri == 'timer:demo?period=3000'
            input.lineNumber == 8;
            outputs.size() == 2
            with(outputs[0], ProcessorDefinition) {
                ref == 'myProcessor'
                lineNumber == 13;
            }
            with(outputs[1], LogDefinition) {
                message == '${body} + ${headers}'
                lineNumber == 15;
            }
        }

        Processor p = context.registry.lookupByNameAndType("myProcessor", Processor.class)
        Assertions.assertNotNull(p)

        Exchange e = new DefaultExchange(context)
        p.process(e)
        e.message.body == 'Hello Camel K!'
    }

    def "integration multiple sources"() {

        // turn on source locations
        context.sourceLocationEnabled = true

        when:
        loadIntegrations('''
            apiVersion: camel.apache.org/v1
            kind: Integration
            metadata:
              name: myapp2
            spec:
              sources:
               - content: |
                   // camel-k: language=java
                   import org.apache.camel.builder.RouteBuilder;
                   public class foo extends RouteBuilder {
                     @Override
                     public void configure() throws Exception {
                         from("timer:demo")
                           .process("myProcessor")
                           .to("log:info");
                     }
                   }
                 name: foo.java
               - content: |
                   beans {
                     myProcessor = processor { 
                       it.in.body = 'Hello Again' 
                     }
                   }
                 name: mybean.groovy
        ''')
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0]) {
            input.endpointUri == 'timer:demo'
            input.lineNumber == 6;
            outputs.size() == 2
            with(outputs[0], ProcessorDefinition) {
                ref == 'myProcessor'
                lineNumber == 7;
            }
            with(outputs[1], ToDefinition) {
                uri == 'log:info'
                lineNumber == 8;
            }
        }

        Processor p = context.registry.lookupByNameAndType("myProcessor", Processor.class)
        Assertions.assertNotNull(p)
        Exchange e = new DefaultExchange(context)
        p.process(e)
        e.message.body == 'Hello Again'
    }

}
