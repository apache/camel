/**
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
package org.apache.camel.blueprint;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.blueprint.handler.CamelNamespaceHandler;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;

public class BlueprintJaxbRestTest extends TestSupport {

    @Test
    public void test() throws Exception {
        if (isJava16() && isJavaVendor("ibm")) {
            // does not test well on java6 with ibm
            return;
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(getClass().getClassLoader().getResourceAsStream("test-rest.xml"));
        Element elem = null;
        NodeList nl = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                elem = (Element) node;
                break;
            }
        }
        CamelNamespaceHandler.doBeforeParse(elem, CamelNamespaceHandler.BLUEPRINT_NS, CamelNamespaceHandler.SPRING_NS);

        JAXBContext context = new BlueprintModelJAXBContextFactory(getClass().getClassLoader()).newJAXBContext();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Object object = unmarshaller.unmarshal(elem);
        assertNotNull(object);
        assertTrue(object instanceof CamelContextFactoryBean);
        assertNotNull(((CamelContextFactoryBean) object).getRoutes());
        assertEquals(0, ((CamelContextFactoryBean) object).getRoutes().size());

        CamelContextFactoryBean cfb = (CamelContextFactoryBean) object;
        assertEquals(2, cfb.getRests().size());
        assertEquals("/say/hello", cfb.getRests().get(0).getPath());
        assertEquals("/say/bye", cfb.getRests().get(1).getPath());

        assertEquals(1, cfb.getRests().get(0).getVerbs().size());
        assertEquals(2, cfb.getRests().get(1).getVerbs().size());
        assertEquals("get", cfb.getRests().get(0).getVerbs().get(0).asVerb());
        assertEquals("get", cfb.getRests().get(1).getVerbs().get(0).asVerb());
        assertEquals("post", cfb.getRests().get(1).getVerbs().get(1).asVerb());

        assertEquals("dummy-rest", cfb.getRestConfiguration().getComponent());
        assertEquals("localhost", cfb.getRestConfiguration().getHost());
        assertEquals("9090", cfb.getRestConfiguration().getPort());
    }
}
