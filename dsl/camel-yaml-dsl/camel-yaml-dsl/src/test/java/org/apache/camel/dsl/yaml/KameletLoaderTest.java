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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ToDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KameletLoaderTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    @Test
    void kameletWithFlow() throws Exception {
        loadKamelets("""
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
                        properties:
                          kbProp: kbValue
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
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);

        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("aws-s3-sink");
        assertThat(rtd.getTemplateParameters().size()).isEqualTo(3);
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "bucketNameOrArn".equals(p.getName()) && p.getDefaultValue() == null)).isTrue();
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "overrideEndpoint".equals(p.getName()) && "false".equals(p.getDefaultValue()))).isTrue();

        BeanFactoryDefinition<RouteTemplateDefinition> firstTemplateBean = rtd.getTemplateBeans().get(0);
        assertThat(firstTemplateBean.getName()).isEqualTo("kameletBean");
        assertThat(firstTemplateBean.getType()).isEqualTo("org.apache.camel.dsl.yaml.KameletBean");
        assertThat(firstTemplateBean.getProperties()).hasSize(1);
        assertThat(firstTemplateBean.getProperties().get("kbProp")).isEqualTo("kbValue");

        BeanFactoryDefinition<RouteTemplateDefinition> secondTemplateBean = rtd.getTemplateBeans().get(1);
        assertThat(secondTemplateBean.getName()).isEqualTo("kameletBean2");
        assertThat(secondTemplateBean.getType()).isEqualTo("org.apache.camel.dsl.yaml.KameletBean");
        assertThat(secondTemplateBean.getProperties()).hasSize(2);
        assertThat(secondTemplateBean.getProperties().get("kbProp2")).isEqualTo("kbValue2");

        assertThat(rtd.getRoute().getInput().getEndpointUri()).isEqualTo("kamelet:source");
        assertThat(rtd.getRoute().getInput().getLineNumber()).isEqualTo(45);
        assertThat(rtd.getRoute().getOutputs()).hasSize(1);
        ToDefinition to = (ToDefinition) rtd.getRoute().getOutputs().get(0);
        assertThat(to.getEndpointUri()).matches("aws2-s3:.*");
        assertThat(to.getLineNumber()).isEqualTo(48);
    }

    @Test
    void kameletWithTemplate() throws Exception {
        loadKamelets("""
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
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);

        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("aws-s3-sink");
        assertThat(rtd.getTemplateParameters().size()).isEqualTo(3);
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "bucketNameOrArn".equals(p.getName()) && p.getDefaultValue() == null)).isTrue();
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "overrideEndpoint".equals(p.getName()) && "false".equals(p.getDefaultValue()))).isTrue();

        assertThat(rtd.getRoute().getInput().getEndpointUri()).isEqualTo("kamelet:source");
        assertThat(rtd.getRoute().getOutputs().size()).isEqualTo(1);
        ToDefinition to = (ToDefinition) rtd.getRoute().getOutputs().get(0);
        assertThat(to.getEndpointUri().matches("aws2-s3:.*")).isTrue();
    }

    @Test
    void kameletWithTemplateAndOptionalParameters() throws Exception {
        loadKamelets("""
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
                """);

        withTemplate(t -> {
            t.to("kamelet:myTemplate?mockName=1&foo=start&bar=1").withBody("Hello 1").send();
            t.to("kamelet:myTemplate?mockName=2&foo=start").withBody("Hello 2").send();
        });

        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("myTemplate");
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "foo".equals(p.getName()) && p.getDefaultValue() == null && p.isRequired())).isTrue();
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "bar".equals(p.getName()) && p.getDefaultValue() == null && !p.isRequired())).isTrue();
    }

    @Test
    void kameletDiscovery() throws Exception {
        String payload = java.util.UUID.randomUUID().toString();

        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:mySetBody?payload=%s"
                      - to: "mock:result"
                """.formatted(payload));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived(payload);
        });

        context.start();

        withTemplate(t -> t.to("direct:start").withBody(payload).send());

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        assertThat(context.getRouteTemplateDefinitions().get(0).getId()).isEqualTo("mySetBody");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletWithFilterAndFlow() throws Exception {
        loadKamelets("""
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
                """);

        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - kamelet:
                          name: "filter-action"
                      - to: "log:route"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived(5, 6, 7));

        context.start();

        withTemplate(t -> {
            for (int i = 1; i <= 10; i++) {
                t.to("direct:start").withBody(i).send();
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletWithDataTypesInput() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-sink
                spec:
                  definition:
                    title: "My Sink"
                    required:
                      - table
                    properties:
                      table:
                        title: Table
                        type: string
                  dataTypes:
                    in:
                      default: json
                      types:
                        json:
                          scheme: my-component
                          format: application-json
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - to: "log:test"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("my-sink");
        assertThat(rtd.getRoute().getInputType().getUrn()).isEqualTo("my-component:application-json");
        assertThat(rtd.getRoute().getOutputType()).isNull();
    }

    @Test
    void kameletWithDataTypesInputAndOutput() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-action
                spec:
                  definition:
                    title: "My Action"
                  dataTypes:
                    in:
                      default: json
                      types:
                        json:
                          scheme: my-component
                          format: application-json
                    out:
                      default: binary
                      types:
                        binary:
                          format: application-octet-stream
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - to: "log:test"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("my-action");
        assertThat(rtd.getRoute().getInputType().getUrn()).isEqualTo("my-component:application-json");
        assertThat(rtd.getRoute().getOutputType().getUrn()).isEqualTo("application-octet-stream");
    }

    @Test
    void kameletWithDataTypesFormatOnly() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-source
                spec:
                  definition:
                    title: "My Source"
                  dataTypes:
                    out:
                      default: binary
                      types:
                        binary:
                          format: application-octet-stream
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - to: "log:test"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("my-source");
        assertThat(rtd.getRoute().getInputType()).isNull();
        assertThat(rtd.getRoute().getOutputType().getUrn()).isEqualTo("application-octet-stream");
    }

    @Test
    void kameletWithLocalBeanViaBeanRef() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: counter-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    title: "Counter Action"
                    properties:
                      start:
                        title: Start
                        description: The starting value for the counter
                        type: integer
                        default: 0
                  template:
                    beans:
                      - name: counter
                        type: java.util.concurrent.atomic.AtomicInteger
                        constructors:
                          "0": "{{start}}"
                    from:
                      uri: kamelet:source
                      steps:
                        - bean:
                            ref: "{{counter}}"
                            method: getAndIncrement
                        - to: "kamelet:sink"
                """);

        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - kamelet:
                          name: "counter-action"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("0"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletWithBeanConstructors() throws Exception {
        loadKamelets("""
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
                        constructors:
                          '0': 123
                          '1': 'Hello World'
                      - name: kameletBean2
                        type: org.apache.camel.dsl.yaml.KameletBean
                        constructors:
                          '0': 123
                          '1': 'Hello World'
                          '2': '#bean:kameletBean'
                        properties:
                          kbProp2: kbValue2
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
                """);

        assertThat(context.getRouteTemplateDefinitions()).hasSize(1);

        RouteTemplateDefinition rtd = context.getRouteTemplateDefinitions().get(0);
        assertThat(rtd.getId()).isEqualTo("aws-s3-sink");
        assertThat(rtd.getTemplateParameters()).hasSize(3);
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "bucketNameOrArn".equals(p.getName()) && p.getDefaultValue() == null)).isTrue();
        assertThat(rtd.getTemplateParameters().stream()
                .anyMatch(p -> "overrideEndpoint".equals(p.getName()) && "false".equals(p.getDefaultValue()))).isTrue();

        assertThat(rtd.getTemplateBeans().get(0).getName()).isEqualTo("kameletBean");
        assertThat(rtd.getTemplateBeans().get(0).getType()).isEqualTo("org.apache.camel.dsl.yaml.KameletBean");
        assertThat(rtd.getTemplateBeans().get(0).getProperties()).isNull();
        assertThat(rtd.getTemplateBeans().get(0).getConstructors().size()).isEqualTo(2);
        assertThat(rtd.getTemplateBeans().get(0).getConstructors().get("0")).isEqualTo("123");
        assertThat(rtd.getTemplateBeans().get(0).getConstructors().get("1")).isEqualTo("Hello World");

        assertThat(rtd.getTemplateBeans().get(1).getName()).isEqualTo("kameletBean2");
        assertThat(rtd.getTemplateBeans().get(1).getType()).isEqualTo("org.apache.camel.dsl.yaml.KameletBean");
        assertThat(rtd.getTemplateBeans().get(1).getProperties().size()).isEqualTo(1);
        assertThat(rtd.getTemplateBeans().get(1).getProperties().get("kbProp2")).isEqualTo("kbValue2");
        assertThat(rtd.getTemplateBeans().get(1).getConstructors().size()).isEqualTo(3);
        assertThat(rtd.getTemplateBeans().get(1).getConstructors().get("0")).isEqualTo("123");
        assertThat(rtd.getTemplateBeans().get(1).getConstructors().get("1")).isEqualTo("Hello World");
        assertThat(rtd.getTemplateBeans().get(1).getConstructors().get("2")).isEqualTo("#bean:kameletBean");

        assertThat(rtd.getRoute().getInput().getEndpointUri()).isEqualTo("kamelet:source");
        assertThat(rtd.getRoute().getInput().getLineNumber()).isEqualTo(48);
        assertThat(rtd.getRoute().getOutputs()).hasSize(1);
        ToDefinition to = (ToDefinition) rtd.getRoute().getOutputs().get(0);
        assertThat(to.getEndpointUri()).matches("aws2-s3:.*");
        assertThat(to.getLineNumber()).isEqualTo(51);
    }
}
