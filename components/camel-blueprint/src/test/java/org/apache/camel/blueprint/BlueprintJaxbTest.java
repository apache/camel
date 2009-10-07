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

import junit.framework.TestCase;

import org.apache.camel.blueprint.handler.CamelNamespaceHandler;

public class BlueprintJaxbTest extends TestCase {

    public void test() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(getClass().getClassLoader().getResourceAsStream("test.xml"));
        Element elem = null;
        NodeList nl = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                elem = (Element) node;
                break;
            }
        }
        CamelNamespaceHandler.renameNamespaceRecursive(elem);

        JAXBContext context = JAXBContext.newInstance("org.apache.camel.blueprint:"
                                                        + "org.apache.camel:org.apache.camel.model:"
                                                        + "org.apache.camel.model.config:"
                                                        + "org.apache.camel.model.dataformat:"
                                                        + "org.apache.camel.model.language:"
                                                        + "org.apache.camel.model.loadbalancer");
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Object object = unmarshaller.unmarshal(elem);
        assertNotNull(object);
        assertTrue(object instanceof CamelContextFactoryBean);
        assertNotNull(((CamelContextFactoryBean) object).getRoutes());
        assertEquals(1, ((CamelContextFactoryBean) object).getRoutes().size());
        assertNotNull(((CamelContextFactoryBean) object).getRoutes().get(0));
        assertNotNull(((CamelContextFactoryBean) object).getRoutes().get(0).getInputs());
        assertEquals(1, ((CamelContextFactoryBean) object).getRoutes().get(0).getInputs().size());
        assertNotNull(((CamelContextFactoryBean) object).getRoutes().get(0).getOutputs());
        assertEquals(1, ((CamelContextFactoryBean) object).getRoutes().get(0).getOutputs().size());
    }
}
