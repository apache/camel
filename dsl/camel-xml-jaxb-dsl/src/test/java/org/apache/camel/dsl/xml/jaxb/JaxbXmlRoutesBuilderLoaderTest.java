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
package org.apache.camel.dsl.xml.jaxb;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JaxbXmlRoutesBuilderLoaderTest {

    private static final String NAMESPACE_ATTRIBUTE = "xmlns=\"http://camel.apache.org/schema/spring\"";
    private final String routesContent = ""
                                         + "<routes " + NAMESPACE_ATTRIBUTE + ">"
                                         + "   <route id=\"xpath-route\">"
                                         + "      <from uri=\"direct:test\"/>"
                                         + "      <setBody>"
                                         + "         <xpath resultType=\"java.lang.String\">"
                                         + "            /foo:orders/order[1]/country/text()"
                                         + "         </xpath>"
                                         + "      </setBody>"
                                         + "   </route>"
                                         + "   <route id=\"timer\">"
                                         + "      <from uri=\"timer:test\"/>"
                                         + "      <log/>"
                                         + "   </route>"
                                         + "</routes>";

    private final String routesTemplateContent = ""
                                                 + "<routeTemplates " + NAMESPACE_ATTRIBUTE + ">"
                                                 + "  <routeTemplate id=\"myTemplate\">"
                                                 + "    <templateParameter name=\"foo\"/>"
                                                 + "    <templateParameter name=\"bar\"/>"
                                                 + "    <route>"
                                                 + "      <from uri=\"direct:{{foo}}\"/>"
                                                 + "      <to uri=\"mock:{{bar}}\"/>"
                                                 + "    </route>"
                                                 + "  </routeTemplate>"
                                                 + "</routeTemplates>";

    private final String restsContent = ""
                                        + "<rests " + NAMESPACE_ATTRIBUTE + ">"
                                        + "  <rest id=\"bar\" path=\"/say/hello\">"
                                        + "    <get path=\"/bar\">"
                                        + "      <to uri=\"mock:bar\"/>"
                                        + "    </get>"
                                        + "  </rest>"
                                        + "</rests>";

    @Test
    public void canLoadRoutes() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(routesContent, false);
        assertFalse(builder.getRouteCollection().getRoutes().isEmpty());
    }

    @Test
    public void canLoadRoutesNoNS() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(routesContent, true);
        assertEquals(2, builder.getRouteCollection().getRoutes().size());
    }

    @Test
    public void canLoadRests() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(restsContent, false);
        assertFalse(builder.getRestCollection().getRests().isEmpty());
    }

    @Test
    public void canLoadRestsNoNS() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(restsContent, true);
        assertFalse(builder.getRestCollection().getRests().isEmpty());
    }

    @Test
    public void canLoadTemplates() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(routesTemplateContent, false);
        assertFalse(builder.getRouteTemplateCollection().getRouteTemplates().isEmpty());
    }

    @Test
    public void canLoadTemplatesNoNS() throws Exception {
        RouteBuilder builder = configureBuilderWithContent(routesTemplateContent, true);
        assertFalse(builder.getRouteTemplateCollection().getRouteTemplates().isEmpty());
    }

    private RouteBuilder configureBuilderWithContent(String content, boolean removeNamespace) throws Exception {
        String xmlContent = content;
        if (removeNamespace) {
            xmlContent = content.replaceFirst("xmlns=\"http://camel.apache.org/schema/spring\"", "");
        }
        Resource resource = ResourceHelper.fromString("in-memory.xml", xmlContent);

        JaxbXmlRoutesBuilderLoader loader = new JaxbXmlRoutesBuilderLoader();
        loader.setCamelContext(new DefaultCamelContext());

        RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);
        builder.setCamelContext(loader.getCamelContext());
        builder.configure();
        return builder;
    }
}
