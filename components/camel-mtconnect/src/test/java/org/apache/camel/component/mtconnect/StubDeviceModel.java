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
package org.apache.camel.component.mtconnect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The stub agent's device model, read from a captured probe document with the JDK's DOM parser, independent of the
 * component's own parser: the data items with the element names their observations carry in a streams document.
 */
final class StubDeviceModel {

    /** A data item and where its observations appear in a streams document. */
    record Item(
            String id, String name, String category, String type, String subType, String units, String nativeUnits,
            String representation, String deviceName, String deviceUuid, String componentType, String componentId,
            String componentName) {

        /** The element name of a sample or event: the type in CamelCase, with its vendor prefix. */
        String elementName() {
            String prefix = type.contains(":") ? type.substring(0, type.indexOf(':') + 1) : "";
            StringBuilder name = new StringBuilder(prefix);
            for (String word : type.substring(prefix.length()).split("_")) {
                name.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
            }
            if ("DATA_SET".equals(representation)) {
                name.append("DataSet");
            }
            return name.toString();
        }

        boolean condition() {
            return category.equals("CONDITION");
        }
    }

    private final byte[] probe;
    private final List<Item> items = new ArrayList<>();

    StubDeviceModel(String resource) {
        try (InputStream in = StubDeviceModel.class.getClassLoader().getResourceAsStream(resource)) {
            probe = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Element devices = (Element) parse().getDocumentElement().getElementsByTagNameNS("*", "Devices").item(0);
        for (Element device : children(devices)) {
            for (Element dataItem : descendants(device, "DataItem")) {
                Element owner = (Element) dataItem.getParentNode().getParentNode();
                items.add(new Item(
                        dataItem.getAttribute("id"), attribute(dataItem, "name"), dataItem.getAttribute("category"),
                        dataItem.getAttribute("type"), attribute(dataItem, "subType"), attribute(dataItem, "units"),
                        attribute(dataItem, "nativeUnits"), attribute(dataItem, "representation"),
                        device.getAttribute("name"), device.getAttribute("uuid"), owner.getLocalName(),
                        owner.getAttribute("id"), attribute(owner, "name")));
            }
        }
    }

    List<Item> items() {
        return items;
    }

    Item item(String id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    boolean hasDevice(String device) {
        return items.stream().anyMatch(item -> matches(item, device));
    }

    /** The uuid of the device with the given name or uuid. */
    String uuid(String device) {
        return items.stream().filter(item -> matches(item, device)).map(Item::deviceUuid).findFirst().orElse(device);
    }

    static boolean matches(Item item, String device) {
        return device == null || device.equals(item.deviceName()) || device.equals(item.deviceUuid());
    }

    /** The probe document with the given header values, limited to one device when a device is given. */
    String probe(String device, long instanceId, int bufferSize) {
        Document document = parse();
        Element header = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Header").item(0);
        header.setAttribute("instanceId", Long.toString(instanceId));
        header.setAttribute("bufferSize", Integer.toString(bufferSize));
        Element devices = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Devices").item(0);
        for (Element candidate : children(devices)) {
            if (device != null && !device.equals(candidate.getAttribute("name"))
                    && !device.equals(candidate.getAttribute("uuid"))) {
                devices.removeChild(candidate);
            }
        }
        try {
            StringWriter writer = new StringWriter();
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Document parse() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(probe));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String attribute(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    private static List<Element> children(Element parent) {
        List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private static List<Element> descendants(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        var nodes = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }
}
