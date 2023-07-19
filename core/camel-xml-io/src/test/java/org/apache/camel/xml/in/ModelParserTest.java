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
package org.apache.camel.xml.in;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.rest.ParamDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModelParserTest {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";
    private static final List<String> REST_XMLS
            = List.of("barRest.xml", "simpleRest.xml", "simpleRestToD.xml", "restAllowedValues.xml");
    private static final List<String> TEMPLATE_XMLS = List.of("barTemplate.xml");
    private static final List<String> TEMPLATED_ROUTE_XMLS = List.of("barTemplatedRoute.xml");
    private static final List<String> BEANS_XMLS
            = List.of("beansEmpty.xml", "beansWithProperties.xml", "beansWithSpringNS.xml");
    private static final List<String> ROUTE_CONFIGURATION_XMLS
            = List.of("errorHandlerConfiguration.xml", "errorHandlerConfigurationRedeliveryPolicyRef.xml");

    @Test
    public void testNoNamespace() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "nonamespace/routeNoNamespace.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path));
        RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
        assertNotNull(routes);
    }

    @Test
    public void testSingleRouteNoNamespace() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "nonamespace/singleRouteNoNamespace.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path));
        RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
        assertNotNull(routes);
    }

    @Test
    public void testSingleTemplatedRouteNoNamespace() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "nonamespace/singleTemplatedRouteNoNamespace.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path));
        TemplatedRoutesDefinition templatedRoutes = parser.parseTemplatedRoutesDefinition().orElse(null);
        assertNotNull(templatedRoutes);
    }

    @Test
    public void testFiles() throws Exception {
        Path dir = getResourceFolder();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> files = list.sorted().filter(Files::isRegularFile).filter(f -> f.endsWith("xml")).toList();
            for (Path path : files) {
                ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
                boolean isRest = REST_XMLS.contains(path.getFileName().toString());
                boolean isTemplate = TEMPLATE_XMLS.contains(path.getFileName().toString());
                boolean isTemplatedRoute = TEMPLATED_ROUTE_XMLS.contains(path.getFileName().toString());
                boolean isBeans = BEANS_XMLS.contains(path.getFileName().toString());
                boolean isConfiguration = ROUTE_CONFIGURATION_XMLS.contains(path.getFileName().toString());
                if (isRest) {
                    RestsDefinition rests = parser.parseRestsDefinition().orElse(null);
                    assertNotNull(rests);
                } else if (isTemplate) {
                    RouteTemplatesDefinition templates = parser.parseRouteTemplatesDefinition().orElse(null);
                    assertNotNull(templates);
                } else if (isTemplatedRoute) {
                    TemplatedRoutesDefinition templatedRoutes = parser.parseTemplatedRoutesDefinition().orElse(null);
                    assertNotNull(templatedRoutes);
                } else if (isConfiguration) {
                    RouteConfigurationsDefinition configurations = parser.parseRouteConfigurationsDefinition().orElse(null);
                    assertNotNull(configurations);
                } else if (!isBeans) {
                    RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
                    assertNotNull(routes);
                }
            }
        }
    }

    @Test
    public void testSimpleString() throws Exception {
        RoutesDefinition routes = new ModelParser(
                new StringReader(
                        "<routes>"
                                 + "  <route id='foo'>" + "    <from uri='my:bar'/>" + "    <to uri='mock:res'/>"
                                 + "  </route>"
                                 + "</routes>"))
                .parseRoutesDefinition().orElse(null);

        assertNotNull(routes);
    }

    @Test
    public void namespaces() throws Exception {
        final String routesXml = "<routes xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                                 + "       xmlns:foo=\"http://camel.apache.org/foo\">\n"
                                 + "   <route id=\"xpath-route\">\n"
                                 + "      <from uri=\"direct:test\"/>\n"
                                 + "      <setBody>\n"
                                 + "         <xpath resultType=\"java.lang.String\">\n"
                                 + "            /foo:orders/order[1]/country/text()\n"
                                 + "         </xpath>\n"
                                 + "      </setBody>\n"
                                 + "   </route>\n"
                                 + "</routes>";
        final RoutesDefinition routes = new ModelParser(new StringReader(routesXml)).parseRoutesDefinition().orElse(null);
        final RouteDefinition route0 = routes.getRoutes().get(0);
        final SetBodyDefinition setBody = (SetBodyDefinition) route0.getOutputs().get(0);
        final XPathExpression xPath = (XPathExpression) setBody.getExpression();
        final Map<String, String> namespaces = xPath.getNamespaces();
        assertNotNull(namespaces);
        assertEquals("http://camel.apache.org/foo", namespaces.get("foo"));
    }

    @Test
    public void testLineNumber() throws Exception {
        Path dir = getResourceFolder();
        File file = new File(dir.toFile(), "setHeader.xml");
        ModelParser parser = new ModelParser(new FileInputStream(file), NAMESPACE);
        RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
        assertNotNull(routes);
        RouteDefinition route = routes.getRoutes().get(0);
        Assertions.assertEquals(22, route.getInput().getLineNumber());
        Assertions.assertEquals(23, route.getOutputs().get(0).getLineNumber());
        Assertions.assertEquals(26, route.getOutputs().get(1).getLineNumber());
    }

    @Test
    public void testLineNumberMultiline() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "multiline.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
        assertNotNull(routes);
        RouteDefinition route = routes.getRoutes().get(0);
        Assertions.assertEquals(22, route.getInput().getLineNumber());
        Assertions.assertEquals(23, route.getOutputs().get(0).getLineNumber());
        Assertions.assertEquals(25, route.getOutputs().get(1).getLineNumber());
    }

    @Test
    public void testRouteProperty() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "routeProperty.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
        assertNotNull(routes);
        RouteDefinition route = routes.getRoutes().get(0);

        PropertyDefinition p1 = route.getRouteProperties().get(0);
        Assertions.assertEquals("a", p1.getKey());
        Assertions.assertEquals("1", p1.getValue());
        PropertyDefinition p2 = route.getRouteProperties().get(1);
        Assertions.assertEquals("b", p2.getKey());
        Assertions.assertEquals("2", p2.getValue());
    }

    @Test
    public void testRestAllowedValues() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "restAllowedValues.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        RestsDefinition rests = parser.parseRestsDefinition().orElse(null);
        assertNotNull(rests);
        RestDefinition rest = rests.getRests().get(0);
        Assertions.assertEquals(2, rest.getVerbs().size());
        VerbDefinition verb = rest.getVerbs().get(0);
        Assertions.assertEquals(1, verb.getParams().size());
        ParamDefinition param = verb.getParams().get(0);
        Assertions.assertEquals(4, param.getAllowableValues().size());
    }

    @Test
    public void testEmptyBeans() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "beansEmpty.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        BeansDefinition beans = parser.parseBeansDefinition().orElse(null);
        assertNotNull(beans);
        assertTrue(beans.getBeans().isEmpty());
        assertTrue(beans.getSpringBeans().isEmpty());
    }

    @Test
    public void testBeansWithProperties() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "beansWithProperties.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        BeansDefinition beans = parser.parseBeansDefinition().orElse(null);
        assertNotNull(beans);
        assertEquals(2, beans.getBeans().size());
        assertTrue(beans.getSpringBeans().isEmpty());

        RegistryBeanDefinition b1 = beans.getBeans().get(0);
        RegistryBeanDefinition b2 = beans.getBeans().get(1);

        assertEquals("b1", b1.getName());
        assertEquals("org.apache.camel.xml.in.ModelParserTest.MyBean", b1.getType());
        assertEquals("v1", b1.getProperties().get("p1"));
        assertEquals("v2", b1.getProperties().get("p2"));
        assertNotNull(b1.getProperties().get("nested"));
        assertEquals("v1a", ((Map<String, Object>) b1.getProperties().get("nested")).get("p1"));
        assertEquals("v2a", ((Map<String, Object>) b1.getProperties().get("nested")).get("p2"));

        assertEquals("b2", b2.getName());
        assertEquals("org.apache.camel.xml.in.ModelParserTest.MyBean", b1.getType());
        assertEquals("v1", b2.getProperties().get("p1"));
        assertEquals("v2", b2.getProperties().get("p2"));
        assertNull(b2.getProperties().get("nested"));
        assertEquals("v1a", b2.getProperties().get("nested.p1"));
        assertEquals("v2a", b2.getProperties().get("nested.p2"));
    }

    @Test
    public void testSpringBeans() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "beansWithSpringNS.xml").toPath();
        final ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        BeansDefinition beans = parser.parseBeansDefinition().orElse(null);
        assertNotNull(beans);
        assertTrue(beans.getBeans().isEmpty());
        assertEquals(2, beans.getSpringBeans().size());
        Document dom = beans.getSpringBeans().get(0).getOwnerDocument();
        StringWriter sw = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(dom), new StreamResult(sw));
        String document = sw.toString();
        assertTrue(document.contains("class=\"java.lang.String\""));

        assertSame(beans.getSpringBeans().get(0).getOwnerDocument(), beans.getSpringBeans().get(1).getOwnerDocument());
        assertEquals("s1", beans.getSpringBeans().get(0).getAttribute("id"));
        assertEquals("s2", beans.getSpringBeans().get(1).getAttribute("id"));

        assertEquals(1, beans.getComponentScanning().size());
        assertEquals("com.example", beans.getComponentScanning().get(0).getBasePackage());
    }

    @Test
    public void testUriLineBreak() throws Exception {
        final String fromFrag1 = "seda:a?concurrentConsumers=2&amp;";
        final String fromFrag2 = "defaultPollTimeout=500";
        final String jpaFrag1 = "jpa:SomeClass?query=update Object o";
        final String jpaSpaces = "        ";
        final String jpaFrag2 = "set o.status = 0";
        final String toFrag1 = "seda:b?";
        final String toFrag2 = "lazyStartProducer=true&amp;";
        final String toFrag3 = "defaultBlockWhenFull=true";
        final String routesXml = "<routes xmlns=\"" + NAMESPACE + "\">\n"
                                 + "  <route>\n"
                                 + "    <from uri=\"" + fromFrag1 + "\n"
                                 + "        " + fromFrag2 + "\n"
                                 + "        \"/>\n"
                                 + "    <to uri=\"" + jpaFrag1 + "\n"
                                 + jpaSpaces + jpaFrag2 + "\"/>\n"
                                 + "    <to uri=\"" + toFrag1 + "\n"
                                 + "        " + toFrag2 + "\n"
                                 + "        " + toFrag3 + "\"/>\n"
                                 + "  </route>\n"
                                 + "</routes>";
        final RoutesDefinition routes
                = new ModelParser(new StringReader(routesXml), NAMESPACE).parseRoutesDefinition().orElse(null);
        final RouteDefinition route = routes.getRoutes().get(0);
        final FromDefinition from = route.getInput();

        final ToDefinition jpa = (ToDefinition) route.getOutputs().get(0);
        final ToDefinition to = (ToDefinition) route.getOutputs().get(1);

        final String fromUri = (fromFrag1 + fromFrag2).replace("&amp;", "&");
        final String jpaUri = jpaFrag1 + " " + jpaSpaces + jpaFrag2; // \n is changed to a single space
        final String toUri = (toFrag1 + toFrag2 + toFrag3).replace("&amp;", "&");

        Assertions.assertEquals(fromUri, from.getEndpointUri());
        Assertions.assertEquals(jpaUri, jpa.getEndpointUri());
        Assertions.assertEquals(toUri, to.getEndpointUri());
    }

    @Test
    public void testErrorHandler() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "errorHandlerConfiguration.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        RouteConfigurationsDefinition routes = parser.parseRouteConfigurationsDefinition().orElse(null);
        assertNotNull(routes);
        assertEquals(1, routes.getRouteConfigurations().size());

        RouteConfigurationDefinition cfg = routes.getRouteConfigurations().get(0);
        assertInstanceOf(DeadLetterChannelDefinition.class, cfg.getErrorHandler().getErrorHandlerType());
        DeadLetterChannelDefinition dlc = (DeadLetterChannelDefinition) cfg.getErrorHandler().getErrorHandlerType();
        assertEquals("mock:dead", dlc.getDeadLetterUri());
        assertTrue(dlc.hasRedeliveryPolicy());
        assertEquals("2", dlc.getRedeliveryPolicy().getMaximumRedeliveries());
        assertEquals("123", dlc.getRedeliveryPolicy().getRedeliveryDelay());
        assertEquals("false", dlc.getRedeliveryPolicy().getLogStackTrace());
    }

    @Test
    public void testErrorHandlerRedeliveryPolicyRef() throws Exception {
        Path dir = getResourceFolder();
        Path path = new File(dir.toFile(), "errorHandlerConfigurationRedeliveryPolicyRef.xml").toPath();
        ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
        RouteConfigurationsDefinition routes = parser.parseRouteConfigurationsDefinition().orElse(null);
        assertNotNull(routes);
        assertEquals(1, routes.getRouteConfigurations().size());

        RouteConfigurationDefinition cfg = routes.getRouteConfigurations().get(0);
        assertInstanceOf(DeadLetterChannelDefinition.class, cfg.getErrorHandler().getErrorHandlerType());
        DeadLetterChannelDefinition dlc = (DeadLetterChannelDefinition) cfg.getErrorHandler().getErrorHandlerType();
        assertEquals("mock:dead", dlc.getDeadLetterUri());
        assertFalse(dlc.hasRedeliveryPolicy());
        assertEquals("myPolicy", dlc.getRedeliveryPolicyRef());
    }

    private Path getResourceFolder() {
        String url = getClass().getClassLoader().getResource("barInterceptorRoute.xml").toString();
        if (url.startsWith("file:")) {
            url = url.substring("file:".length(), url.indexOf("barInterceptorRoute.xml"));
        } else if (url.startsWith("jar:file:")) {
            url = url.substring("jar:file:".length(), url.indexOf('!'));
        }
        return Paths.get(url);
    }
}
