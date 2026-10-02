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
package org.apache.camel.dsl.yaml.validator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.networknt.schema.Error;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.xml.LwModelToXMLDumper;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rests and route templates dumped as YAML are loaded back by the YAML DSL as they were (CAMEL-25255): the YAML writer
 * writes the verbs of a rest by their kind (get, post, ...), and the parameters and beans of a route template as the
 * YAML DSL names them.
 */
class YamlRestTemplateRoundTripTest {

    private static final String NS = "http://camel.apache.org/schema/xml-io";
    private static final Path CORPUS = Path.of("../../../core/camel-xml-io/src/test/resources");

    private static final String REST = """
            <rests xmlns="http://camel.apache.org/schema/xml-io">
                <rest id="api" path="/api" tag="orders" consumes="application/json" bindingMode="json">
                    <securityDefinitions>
                        <apiKey key="key" name="X-Key" inHeader="true"/>
                        <oauth2 key="oauth" flow="implicit" authorizationUrl="https://auth/authorize">
                            <scopes key="read" value="Read orders"/>
                        </oauth2>
                    </securityDefinitions>
                    <securityRequirements key="key"/>
                    <get path="/orders/{id}" id="getOrder" outType="com.foo.Order" routeId="get-order"
                         description="Gets an order">
                        <param name="id" type="path" description="The order id" dataType="integer">
                            <examples key="one" value="1"/>
                        </param>
                        <param name="verbose" type="query" required="false" defaultValue="false">
                            <allowableValues>
                                <value>true</value>
                                <value>false</value>
                            </allowableValues>
                        </param>
                        <responseMessage code="404" message="Not found">
                            <header name="X-Reason" dataType="string"/>
                            <examples key="json" value="{}"/>
                        </responseMessage>
                        <security key="oauth" scopes="read"/>
                        <to uri="direct:getOrder"/>
                    </get>
                    <get path="/orders">
                        <to uri="direct:listOrders"/>
                    </get>
                    <post path="/orders" type="com.foo.Order">
                        <to uri="direct:createOrder"/>
                    </post>
                    <put path="/orders/{id}">
                        <to uri="direct:updateOrder"/>
                    </put>
                    <delete path="/orders/{id}">
                        <to uri="direct:deleteOrder"/>
                    </delete>
                    <head path="/orders">
                        <to uri="direct:headOrders"/>
                    </head>
                    <patch path="/orders/{id}">
                        <to uri="direct:patchOrder"/>
                    </patch>
                </rest>
                <rest path="/petstore">
                    <openApi specification="petstore.json" missingOperation="mock"/>
                </rest>
            </rests>
            """;

    private static final String TEMPLATE = """
            <routeTemplates xmlns="http://camel.apache.org/schema/xml-io">
                <routeTemplate id="myTemplate">
                    <templateParameter name="foo" description="The input"/>
                    <templateParameter name="bar" defaultValue="out" required="false"/>
                    <templateBean name="greeter" type="com.foo.Greeter">
                        <properties>
                            <property key="greeting" value="Hello"/>
                        </properties>
                    </templateBean>
                    <route>
                        <from uri="direct:{{foo}}"/>
                        <to uri="mock:{{bar}}"/>
                    </route>
                </routeTemplate>
            </routeTemplates>
            """;

    @Test
    void restWithAllItsParts() throws Exception {
        assertThat(restsRoundTrip(REST)).isNull();
    }

    @Test
    void routeTemplateWithParametersAndBeans() throws Exception {
        assertThat(templatesRoundTrip(TEMPLATE)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "barRest.xml", "simpleRest.xml", "restAllowedValues.xml" })
    void restsOfTheCorpus(String file) throws Exception {
        assumeTrue(Files.isDirectory(CORPUS), "the core test routes are in the source tree");
        assertThat(restsRoundTrip(Files.readString(CORPUS.resolve(file)))).isNull();
    }

    @Test
    void routeTemplatesOfTheCorpus() throws Exception {
        assumeTrue(Files.isDirectory(CORPUS), "the core test routes are in the source tree");
        assertThat(templatesRoundTrip(Files.readString(CORPUS.resolve("barTemplate.xml")))).isNull();
    }

    private static InputStream in(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Null when the rests read back as they were, else what went wrong. Both sides are added to a context the same way,
     * so what loading adds (such as the params of a path) is on both.
     */
    private static String restsRoundTrip(String xml) throws Exception {
        RestsDefinition rests = new ModelParser(in(xml), NS).parseRestsDefinition().orElseThrow();
        String yaml;
        String before;
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, rests);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    getRestCollection().setRests(rests.getRests());
                }
            });
            before = dumpRests(context);
        }
        return readBack(yaml, before, YamlRestTemplateRoundTripTest::dumpRests);
    }

    /** Null when the route templates read back as they were, else what went wrong. */
    private static String templatesRoundTrip(String xml) throws Exception {
        RouteTemplatesDefinition templates = new ModelParser(in(xml), NS).parseRouteTemplatesDefinition().orElseThrow();
        String yaml;
        String before;
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, templates);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    getRouteTemplateCollection().setRouteTemplates(templates.getRouteTemplates());
                }
            });
            before = dumpTemplates(context);
        }
        return readBack(yaml, before, YamlRestTemplateRoundTripTest::dumpTemplates);
    }

    private static String dumpRests(DefaultCamelContext context) throws Exception {
        RestsDefinition rests = new RestsDefinition();
        rests.setRests(context.getCamelContextExtension().getContextPlugin(ModelCamelContext.class).getRestDefinitions());
        return new LwModelToXMLDumper().dumpModelAsXml(context, rests);
    }

    private static String dumpTemplates(DefaultCamelContext context) throws Exception {
        RouteTemplatesDefinition templates = new RouteTemplatesDefinition();
        templates.setRouteTemplates(context.getCamelContextExtension().getContextPlugin(ModelCamelContext.class)
                .getRouteTemplateDefinitions());
        return new LwModelToXMLDumper().dumpModelAsXml(context, templates);
    }

    private interface Dump {
        String dump(DefaultCamelContext context) throws Exception;
    }

    private static String readBack(String yaml, String before, Dump dump) throws Exception {
        List<Error> errors = new YamlValidator().validate(yaml);
        if (!errors.isEmpty()) {
            return yaml + "\n--- does not validate: " + errors;
        }
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("rest.yaml", yaml));
            String after = dump.dump(context);
            return after.equals(before) ? null : yaml + "\n--- was ---\n" + before + "\n--- read back as ---\n" + after;
        } catch (RuntimeException e) {
            return yaml + "\n--- does not load: " + e;
        }
    }
}
