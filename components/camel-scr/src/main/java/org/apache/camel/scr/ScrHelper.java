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
package org.apache.camel.scr;

import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for reading properties from a component description file. Used in unit testing.
 */
public final class ScrHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ScrHelper.class);

    private ScrHelper() {
    }

    public static Map<String, String> getScrProperties(String componentName) throws Exception {
        return getScrProperties(String.format("target/classes/OSGI-INF/%s.xml", componentName), componentName);
    }

    public static Map<String, String> getScrProperties(String xmlLocation, String componentName) throws Exception {
        Map<String, String> result = new HashMap<>();

        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        XMLEventReader eventReader = inputFactory.createXMLEventReader(new FileReader(xmlLocation));
        boolean collect = false;
        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();
            if (event.getEventType() == XMLStreamConstants.START_ELEMENT
                && event.asStartElement().getName().toString().equals("scr:component")
                && event.asStartElement().getAttributeByName(QName.valueOf("name")).getValue().equals(componentName)) {
                collect = true;
            } else if (collect
                && event.getEventType() == XMLStreamConstants.START_ELEMENT
                && event.asStartElement().getName().toString().equals("property")) {
                result.put(event.asStartElement().getAttributeByName(QName.valueOf("name")).getValue(), event.asStartElement().getAttributeByName(QName.valueOf("value")).getValue());
            } else if (collect
                && event.getEventType() == XMLStreamConstants.END_ELEMENT
                && event.asEndElement().getName().toString().equals("scr:component")) {
                break;
            }
        }
        return result;
    }
}