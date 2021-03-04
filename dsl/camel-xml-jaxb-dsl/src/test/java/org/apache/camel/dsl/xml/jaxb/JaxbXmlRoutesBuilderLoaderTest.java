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

import static org.junit.jupiter.api.Assertions.assertFalse;

public class JaxbXmlRoutesBuilderLoaderTest {
    @Test
    public void canLoadRoutes() throws Exception {
        String content = ""
                         + "<routes xmlns=\"http://camel.apache.org/schema/spring\">"
                         + "   <route id=\"xpath-route\">"
                         + "      <from uri=\"direct:test\"/>"
                         + "      <setBody>"
                         + "         <xpath resultType=\"java.lang.String\">"
                         + "            /foo:orders/order[1]/country/text()"
                         + "         </xpath>"
                         + "      </setBody>"
                         + "   </route>"
                         + "</routes>";

        Resource resource = ResourceHelper.fromString("in-memory.xml", content);

        JaxbXmlRoutesBuilderLoader loader = new JaxbXmlRoutesBuilderLoader();
        loader.setCamelContext(new DefaultCamelContext());

        RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);
        builder.setContext(loader.getCamelContext());
        builder.configure();

        assertFalse(builder.getRouteCollection().getRoutes().isEmpty());
    }

    @Test
    public void canLoadRests() throws Exception {
        String content = ""
                         + "<rests xmlns=\"http://camel.apache.org/schema/spring\">"
                         + "  <rest id=\"bar\" path=\"/say/hello\">"
                         + "    <get uri=\"/bar\">"
                         + "      <to uri=\"mock:bar\"/>"
                         + "    </get>"
                         + "  </rest>"
                         + "</rests>";

        Resource resource = ResourceHelper.fromString("in-memory.xml", content);

        JaxbXmlRoutesBuilderLoader loader = new JaxbXmlRoutesBuilderLoader();
        loader.setCamelContext(new DefaultCamelContext());

        RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);
        builder.setContext(loader.getCamelContext());
        builder.configure();

        assertFalse(builder.getRestCollection().getRests().isEmpty());
    }

    @Test
    public void canLoadTemplates() throws Exception {
        String content = ""
                         + "<routeTemplates xmlns=\"http://camel.apache.org/schema/spring\">"
                         + "  <routeTemplate id=\"myTemplate\">"
                         + "    <templateParameter name=\"foo\"/>"
                         + "    <templateParameter name=\"bar\"/>"
                         + "    <route>"
                         + "      <from uri=\"direct:{{foo}}\"/>"
                         + "      <to uri=\"mock:{{bar}}\"/>"
                         + "    </route>"
                         + "  </routeTemplate>"
                         + "</routeTemplates>";

        Resource resource = ResourceHelper.fromString("in-memory.xml", content);

        JaxbXmlRoutesBuilderLoader loader = new JaxbXmlRoutesBuilderLoader();
        loader.setCamelContext(new DefaultCamelContext());

        RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);
        builder.setContext(loader.getCamelContext());
        builder.configure();

        assertFalse(builder.getRouteTemplateCollection().getRouteTemplates().isEmpty());
    }
}
