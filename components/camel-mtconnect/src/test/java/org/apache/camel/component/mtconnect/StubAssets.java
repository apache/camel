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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The stub agent's asset store, seeded from a captured MTConnectAssets document and answered in its shape: the captured
 * root element with its namespace declarations, a header with the stub's instance id, and the selected assets, the most
 * recently changed first, as cppagent 2.8 orders them. Read with the JDK's DOM parser, independent of the component's
 * own parser.
 */
final class StubAssets {

    /** One asset as the store holds it. */
    record Asset(String id, String type, String deviceUuid, boolean removed, Element element) {
    }

    private final Document template;
    private final Map<String, Asset> assets = new LinkedHashMap<>();

    /** An empty store when the resource is null. */
    StubAssets(String resource) {
        if (resource == null) {
            template = null;
            return;
        }
        try (InputStream in = StubAssets.class.getClassLoader().getResourceAsStream(resource)) {
            template = parse(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (Element element : children(assetsElement(template))) {
            put(element);
        }
    }

    boolean isEmpty() {
        return assets.isEmpty();
    }

    Asset get(String id) {
        return assets.get(id);
    }

    /** The assets of a device and type, at most count, removed ones only when asked for. */
    List<Asset> select(String device, String type, int count, boolean removed) {
        List<Asset> selected = new ArrayList<>();
        for (Asset asset : newestFirst()) {
            if (selected.size() == count) {
                break;
            }
            if ((device == null || device.equals(asset.deviceUuid())) && (type == null || type.equals(asset.type()))
                    && (removed || !asset.removed())) {
                selected.add(asset);
            }
        }
        return selected;
    }

    /** Gives an asset a new timestamp, as an agent does when the asset is written again. */
    Asset touch(String id, Instant timestamp) {
        Element element = assets.get(id).element();
        element.setAttribute("timestamp", timestamp.toString());
        return put(element);
    }

    /** Adds an asset element, written in the default namespace of the captured document. */
    Asset add(String elementXml) {
        String namespace = template.getDocumentElement().getNamespaceURI();
        Document wrapper = parse(("<wrapper xmlns=\"" + namespace + "\">" + elementXml + "</wrapper>")
                .getBytes(StandardCharsets.UTF_8));
        return put((Element) template.importNode(children(wrapper.getDocumentElement()).get(0), true));
    }

    /** Drops an asset from the store, as an agent whose asset buffer no longer holds it. */
    Asset forget(String id) {
        return assets.remove(id);
    }

    /** Marks an asset removed with a new timestamp, as an agent does on a delete. */
    Asset remove(String id, Instant timestamp) {
        Element element = assets.get(id).element();
        element.setAttribute("removed", "true");
        element.setAttribute("timestamp", timestamp.toString());
        return put(element);
    }

    /** An MTConnectAssets document with the given assets. */
    String document(long instanceId, List<Asset> selected) {
        Document document = (Document) template.cloneNode(true);
        Element header = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Header").item(0);
        header.setAttribute("instanceId", Long.toString(instanceId));
        header.setAttribute("assetCount", Long.toString(assets.values().stream().filter(a -> !a.removed()).count()));
        Element container = assetsElement(document);
        while (container.getFirstChild() != null) {
            container.removeChild(container.getFirstChild());
        }
        for (Asset asset : selected) {
            container.appendChild(document.importNode(asset.element(), true));
        }
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Asset put(Element element) {
        String id = element.getAttribute("assetId");
        Asset asset = new Asset(
                id, element.getLocalName(), element.getAttribute("deviceUuid"),
                "true".equals(element.getAttribute("removed")), element);
        // the most recently written asset moves to the end, and the store answers newest first
        assets.remove(id);
        assets.put(id, asset);
        return asset;
    }

    private List<Asset> newestFirst() {
        List<Asset> ordered = new ArrayList<>(assets.values());
        Collections.reverse(ordered);
        return ordered;
    }

    private static Element assetsElement(Document document) {
        return (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Assets").item(0);
    }

    private static Document parse(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
}
