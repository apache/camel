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

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.RouteTemplateDefinition
import org.apache.camel.model.ToDefinition

class KameletLoaderTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "kamelet with flow"() {
        when:
            loadKamelets('''
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: aws-s3-sink                  
                spec:
                  definition:
                    title: "AWS S3 Sink"
                    description: "AWS S3 Sink"
                    required:
                      - bucketNameOrArn
                      - accessKey
                      - secretKey
                      - region
                    type: object
                    properties:
                      bucketNameOrArn:
                        title: Bucket Name
                        description: The S3 Bucket name or ARN.
                        type: string
                      accessKey:
                        title: Access Key
                        description: The access key obtained from AWS.
                        type: string
                        format: password
                        x-descriptors:
                        - urn:alm:descriptor:com.tectonic.ui:password
                      overrideEndpoint:
                        title: Override Endpoint
                        type: boolean
                        default: false
                        x-descriptors:
                        - 'urn:alm:descriptor:com.tectonic.ui:checkbox'
                  template:
                    beans:
                      - name: kameletBean
                        type: org.apache.camel.dsl.yaml.KameletBean
                        property:
                          - key: kbProp
                            value: kbValue
                      - name: kameletBean2
                        type: org.apache.camel.dsl.yaml.KameletBean
                        properties:
                          kbProp2: kbValue2
                          kbObjProp:
                            kbObjPropProp: kbObjPropPropVal
                    from:
                      uri: "kamelet:source"
                      steps:
                      - to:
                          uri: "aws2-s3:{{bucketNameOrArn}}"
                          parameters:
                            secretKey: "{{secretKey}}"
                            accessKey: "{{accessKey}}"
                            region: "{{region}}"
                            uriEndpointOverride: "{{uriEndpointOverride}}"
                            overrideEndpoint: "{{overrideEndpoint}}"
                            autoCreateBucket: "{{autoCreateBucket}}"
            ''')
        then:
            context.routeTemplateDefinitions.size() == 1

            with (context.routeTemplateDefinitions[0]) {
                id == 'aws-s3-sink'

                templateParameters.size() == 3

                templateParameters.any {
                    it.name == 'bucketNameOrArn' && it.defaultValue == null
                }
                templateParameters.any {
                    it.name == 'overrideEndpoint' && it.defaultValue == 'false'
                }

                with(route) {
                    input.endpointUri == 'kamelet:source'
                    input.lineNumber == 47
                    outputs.size() == 1
                    with (outputs[0], ToDefinition) {
                        endpointUri ==~ /aws2-s3:.*/
                        lineNumber == 50
                    }
                }
            }
    }

    def "kamelet with template"() {
        when:
            loadKamelets('''
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: aws-s3-sink                  
                spec:
                  definition:
                    title: "AWS S3 Sink"
                    description: "AWS S3 Sink"
                    required:
                      - bucketNameOrArn
                      - accessKey
                      - secretKey
                      - region
                    type: object
                    properties:
                      bucketNameOrArn:
                        title: Bucket Name
                        description: The S3 Bucket name or ARN.
                        type: string
                      accessKey:
                        title: Access Key
                        description: The access key obtained from AWS.
                        type: string
                        format: password
                        x-descriptors:
                        - urn:alm:descriptor:com.tectonic.ui:password
                      overrideEndpoint:
                        title: Override Endpoint
                        type: boolean
                        default: false
                        x-descriptors:
                        - 'urn:alm:descriptor:com.tectonic.ui:checkbox'
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                      - to:
                          uri: "aws2-s3:{{bucketNameOrArn}}"
                          parameters:
                            secretKey: "{{secretKey}}"
                            accessKey: "{{accessKey}}"
                            region: "{{region}}"
                            uriEndpointOverride: "{{uriEndpointOverride}}"
                            overrideEndpoint: "{{overrideEndpoint}}"
                            autoCreateBucket: "{{autoCreateBucket}}"
            ''')
        then:
            context.routeTemplateDefinitions.size() == 1

            with (context.routeTemplateDefinitions[0]) {
                id == 'aws-s3-sink'

                templateParameters.size() == 3

                templateParameters.any {
                    it.name == 'bucketNameOrArn' && it.defaultValue == null
                }
                templateParameters.any {
                    it.name == 'overrideEndpoint' && it.defaultValue == 'false'
                }

                with(route) {
                    input.endpointUri == 'kamelet:source'
                    outputs.size() == 1
                    with (outputs[0], ToDefinition) {
                        endpointUri ==~ /aws2-s3:.*/
                    }
                }
            }
    }

    def "kamelet with template and optional parameters"() {
        setup:
            loadKamelets """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: myTemplate
                spec:
                  definition:
                    required:
                      - foo
                      - mockName
                    properties:
                      mockName:
                        type: string  
                      foo:
                        type: string
                      bar:
                        type: string
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - to: "mock:{{mockName}}?retainFirst={{?bar}}"
            """
        when:
            withTemplate {
                to('kamelet:myTemplate?mockName=1&foo=start&bar=1').withBody('Hello 1').send()
                to('kamelet:myTemplate?mockName=2&foo=start').withBody('Hello 2').send()
            }
        then:
            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'

                templateParameters.any {
                    it.name == 'foo' && it.defaultValue == null && it.isRequired()
                }
                templateParameters.any {
                    it.name == 'bar' && it.defaultValue == null && !it.isRequired()
                }
            }
            with(context.getEndpoint("mock:1?retainFirst=1", MockEndpoint)) {
                expectedBodiesReceived("Hello World")
                assertIsSatisfied()
            }
            with(context.getEndpoint("mock:2", MockEndpoint)) {
                expectedBodiesReceived("Bye World")
                assertIsSatisfied()
            }
    }

    def "kamelet discovery"() {
        setup:
            def payload = UUID.randomUUID().toString()

            loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:mySetBody?payload=${payload}"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived payload
            }
        when:
            context.start()

            withTemplate {
                to('direct:start').withBody(payload).send()
            }
        then:
            context.routeTemplateDefinitions.size() == 1

            with (context.routeTemplateDefinitions[0]) {
                id == 'mySetBody'
            }

            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet with filter and flow"() {
        setup:
            loadKamelets '''
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: filter-action
                spec:
                  definition:
                    title: "Filter"
                    description: "Filter based on the body"
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                      - filter:
                          simple: "${body} range '5..7'"
                      - to: "log:filter"
                      - to: "kamelet:sink"    
            '''

            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - kamelet:
                          name: "filter-action"
                      - to: "log:route"
                      - to: "mock:result"
            '''

            withMock('mock:result') {
                expectedBodiesReceived 5, 6, 7
            }

        when:
            context.start()

            withTemplate {
                (1..10).each {
                    to('direct:start')
                        .withBody(it)
                        .send()
                }
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
