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
package org.apache.camel.util;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DumpModelEdgeCasesTest extends ContextTestSupport {

    @Test
    public void testDumpBeansAsXmlIsWellFormed() throws Exception {
        BeanFactoryDefinition<?> bean = new BeanFactoryDefinition<>();
        bean.setName("myBean");
        bean.setType("com.foo.MyBean");
        bean.setScriptLanguage("groovy");
        bean.setScript("return a < b && c");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", "http://host?a=1&b=2");
        props.put("text", "say \"hi\"");
        bean.setProperties(props);

        String xml = PluginHelper.getModelToXMLDumper(context).dumpBeansAsXml(context, List.of(bean));
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader("<beans>" + xml + "</beans>")));
        assertThat(doc.getElementsByTagName("bean").item(0).getAttributes().getNamedItem("scriptLanguage").getNodeValue())
                .isEqualTo("groovy");
        assertThat(doc.getElementsByTagName("script").item(0).getTextContent().trim()).isEqualTo("return a < b && c");
        assertThat(doc.getElementsByTagName("property").item(0).getAttributes().getNamedItem("value").getNodeValue())
                .isEqualTo("http://host?a=1&b=2");
    }

    @Test
    public void testDumpWithoutNote() throws Exception {
        // a note is a code comment, which is not dumped
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertThat(xml).contains("myRoute").doesNotContain("note");

        String yaml
                = PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, context.getRouteDefinition("myRoute"));
        assertThat(yaml).contains("myRoute").doesNotContain("note");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").note("my route note")
                        .log("Hello").note("my log note")
                        .to("mock:result");
            }
        };
    }
}
