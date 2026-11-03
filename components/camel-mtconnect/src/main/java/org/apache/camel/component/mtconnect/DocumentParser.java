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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses agent documents into the model. Elements are matched by local name, so that every MTConnect schema namespace
 * from 1.x to 2.x and vendor namespaces such as Mazak's parse the same way. External entities and DTDs are disabled.
 */
final class DocumentParser {

    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final XMLInputFactory factory;
    private final AssetWriter assetWriter = new AssetWriter();

    DocumentParser() {
        factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /**
     * Parses a probe, streams, assets or error document.
     *
     * @throws XMLStreamException when the content is not well-formed XML or not an MTConnect document
     */
    AgentDocument parse(byte[] content) throws XMLStreamException {
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(content));
        try {
            reader.nextTag();
            String root = reader.getLocalName();
            return switch (root) {
                case "MTConnectDevices" -> probe(reader);
                case "MTConnectStreams" -> streams(reader);
                case "MTConnectAssets" -> assets(reader);
                case "MTConnectError" -> error(reader);
                default -> throw new XMLStreamException("Not an MTConnect document: root element " + root);
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new XMLStreamException("Invalid MTConnect document: " + e.getMessage(), e);
        } finally {
            reader.close();
        }
    }

    private AgentDocument.Probe probe(XMLStreamReader reader) throws XMLStreamException {
        DocumentHeader header = null;
        Map<String, DataItem> dataItems = new LinkedHashMap<>();
        List<Element> open = new ArrayList<>();
        open.add(element(reader));
        Element device = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                Element element = element(reader);
                Element parent = open.get(open.size() - 1);
                if (open.size() == 1 && element.localName().equals("Header")) {
                    header = header(reader);
                } else if (parent.localName().equals("Devices")) {
                    device = element;
                } else if (device != null && element.localName().equals("DataItem")
                        && parent.localName().equals("DataItems")) {
                    Element owner = open.get(open.size() - 2);
                    dataItems.put(element.id(), dataItem(reader, device, owner));
                }
                open.add(element);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                open.remove(open.size() - 1);
            }
        }
        return new AgentDocument.Probe(required(header), dataItems);
    }

    private static DataItem dataItem(XMLStreamReader reader, Element device, Element owner) {
        // a data item directly under the device belongs to the Device (or Agent) component, as in the streams
        return new DataItem(
                attribute(reader, "id"), attribute(reader, "name"), attribute(reader, "category"),
                attribute(reader, "type"), attribute(reader, "subType"), attribute(reader, "units"),
                attribute(reader, "nativeUnits"), attribute(reader, "representation"), device.uuid(), device.name(),
                owner.id(), owner.localName(), owner.name());
    }

    private AgentDocument.Streams streams(XMLStreamReader reader) throws XMLStreamException {
        DocumentHeader header = null;
        List<Observation> observations = new ArrayList<>();
        List<Element> open = new ArrayList<>();
        open.add(element(reader));
        Element device = null;
        Element component = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                String category = category(open.get(open.size() - 1).localName());
                if (open.size() == 1 && localName.equals("Header")) {
                    header = header(reader);
                } else if (localName.equals("DeviceStream")) {
                    device = element(reader);
                } else if (localName.equals("ComponentStream")) {
                    component = new Element(
                            attribute(reader, "component"), attribute(reader, "componentId"), attribute(reader, "name"),
                            null);
                } else if (category != null && device != null && component != null) {
                    observations.add(observation(reader, device, component, category));
                    continue;
                }
                open.add(element(reader));
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                open.remove(open.size() - 1);
            }
        }
        observations.sort(Comparator.comparingLong(Observation::sequence));
        return new AgentDocument.Streams(required(header), observations);
    }

    private static String category(String container) {
        return switch (container) {
            case "Samples" -> "SAMPLE";
            case "Events" -> "EVENT";
            case "Condition" -> "CONDITION";
            default -> null;
        };
    }

    /** Reads one observation element up to its end tag. */
    private static Observation observation(XMLStreamReader reader, Element device, Element component, String category)
            throws XMLStreamException {
        String elementName = reader.getLocalName();
        String dataItemId = attribute(reader, "dataItemId");
        String sequence = attribute(reader, "sequence");
        if (dataItemId == null || sequence == null) {
            throw new XMLStreamException("Observation " + elementName + " without dataItemId or sequence");
        }
        Instant timestamp = timestamp(attribute(reader, "timestamp"));
        String name = attribute(reader, "name");
        String subType = attribute(reader, "subType");
        String type = attribute(reader, "type");
        Observation.Attributes attributes = new Observation.Attributes(
                attribute(reader, "nativeCode"), attribute(reader, "nativeSeverity"), attribute(reader, "qualifier"),
                attribute(reader, "compositionId"), attribute(reader, "resetTriggered"), attribute(reader, "statistic"),
                decimal(attribute(reader, "duration")), decimal(attribute(reader, "sampleRate")),
                attribute(reader, "assetType"));
        boolean sample = category.equals("SAMPLE");
        StringBuilder text = new StringBuilder();
        Map<String, Object> entries = null;
        while (true) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(reader.getText());
            } else if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("Entry")) {
                entries = entries == null ? new LinkedHashMap<>() : entries;
                String key = attribute(reader, "key");
                boolean removed = "true".equalsIgnoreCase(attribute(reader, "removed"));
                Object entry = entry(reader, sample);
                entries.put(key, removed ? null : entry);
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                skip(reader);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        String content = text.toString();
        boolean unavailable;
        Object value;
        if (category.equals("CONDITION")) {
            unavailable = elementName.equals("Unavailable");
            value = content.isBlank() ? null : content.trim();
        } else if (entries != null) {
            unavailable = false;
            value = entries;
        } else if (content.trim().equals(UNAVAILABLE)) {
            unavailable = true;
            value = null;
        } else {
            unavailable = false;
            value = value(elementName, content, sample);
        }
        return new Observation(
                device.uuid(), device.name(), component.id(), component.localName(), component.name(), dataItemId,
                category, elementName, Long.parseLong(sequence), timestamp, name, subType, type, attributes, value,
                unavailable);
    }

    private static Object value(String elementName, String content, boolean sample) {
        if (elementName.endsWith("DataSet") || elementName.endsWith("Table")) {
            return new LinkedHashMap<String, Object>();
        }
        if (!sample) {
            return content;
        }
        if (elementName.endsWith("TimeSeries")) {
            List<BigDecimal> series = new ArrayList<>();
            for (String token : content.trim().split("\\s+")) {
                BigDecimal number = decimal(token);
                if (number == null) {
                    return content;
                }
                series.add(number);
            }
            return series;
        }
        BigDecimal number = decimal(content.trim());
        return number == null ? content : number;
    }

    /** Reads the content of an Entry: its text, or a map of its Cell elements for a table. */
    private static Object entry(XMLStreamReader reader, boolean sample) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        Map<String, Object> cells = null;
        while (true) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(reader.getText());
            } else if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("Cell")) {
                cells = cells == null ? new LinkedHashMap<>() : cells;
                String key = attribute(reader, "key");
                cells.put(key, typed(reader.getElementText(), sample));
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                skip(reader);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return cells != null ? cells : typed(text.toString(), sample);
            }
        }
    }

    private static Object typed(String text, boolean sample) {
        BigDecimal number = sample ? decimal(text.trim()) : null;
        return number == null ? text : number;
    }

    private static BigDecimal decimal(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reads an assets document; each child of its Assets element is an asset, copied as a standalone document. */
    private AgentDocument.Assets assets(XMLStreamReader reader) throws XMLStreamException {
        DocumentHeader header = null;
        List<AgentDocument.Asset> assets = new ArrayList<>();
        // the namespace declarations of the open elements, root first
        List<Map<String, String>> scopes = new ArrayList<>();
        List<String> open = new ArrayList<>();
        scopes.add(namespaces(reader));
        open.add(reader.getLocalName());
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (open.size() == 1 && reader.getLocalName().equals("Header")) {
                    header = header(reader);
                } else if (open.get(open.size() - 1).equals("Assets")) {
                    Map<String, String> inherited = new LinkedHashMap<>();
                    scopes.forEach(inherited::putAll);
                    assets.add(asset(reader, inherited));
                    continue;
                }
                scopes.add(namespaces(reader));
                open.add(reader.getLocalName());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                scopes.remove(scopes.size() - 1);
                open.remove(open.size() - 1);
            }
        }
        return new AgentDocument.Assets(required(header), assets);
    }

    private AgentDocument.Asset asset(XMLStreamReader reader, Map<String, String> inherited) throws XMLStreamException {
        String assetId = attribute(reader, "assetId");
        String type = reader.getLocalName();
        Instant timestamp = timestamp(attribute(reader, "timestamp"));
        String deviceUuid = attribute(reader, "deviceUuid");
        boolean removed = "true".equalsIgnoreCase(attribute(reader, "removed"));
        if (assetId == null) {
            throw new XMLStreamException("Asset " + type + " without assetId");
        }
        return new AgentDocument.Asset(assetId, type, timestamp, deviceUuid, removed, assetWriter.copy(reader, inherited));
    }

    private static Map<String, String> namespaces(XMLStreamReader reader) {
        Map<String, String> declared = new LinkedHashMap<>();
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            String prefix = reader.getNamespacePrefix(i);
            declared.put(prefix == null ? "" : prefix, reader.getNamespaceURI(i));
        }
        return declared;
    }

    private AgentDocument.Error error(XMLStreamReader reader) throws XMLStreamException {
        DocumentHeader header = null;
        List<AgentDocument.AgentError> errors = new ArrayList<>();
        int depth = 1;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if (depth == 1 && localName.equals("Header")) {
                    header = header(reader);
                } else if (localName.equals("Error")) {
                    String code = attribute(reader, "errorCode");
                    errors.add(new AgentDocument.AgentError(code, reader.getElementText().trim()));
                    continue;
                }
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return new AgentDocument.Error(
                header == null ? new DocumentHeader(null, null, null, null, null, null) : header, errors);
    }

    private static DocumentHeader header(XMLStreamReader reader) {
        return new DocumentHeader(
                number(reader, "instanceId"), number(reader, "bufferSize"), number(reader, "assetBufferSize"),
                number(reader, "firstSequence"),
                number(reader, "nextSequence"), number(reader, "lastSequence"));
    }

    private static DocumentHeader required(DocumentHeader header) throws XMLStreamException {
        if (header == null) {
            throw new XMLStreamException("MTConnect document without a Header element");
        }
        return header;
    }

    private static Long number(XMLStreamReader reader, String attribute) {
        String value = attribute(reader, attribute);
        return value == null ? null : Long.valueOf(value.trim());
    }

    private static Instant timestamp(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static void skip(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static Element element(XMLStreamReader reader) {
        return new Element(
                reader.getLocalName(), attribute(reader, "id"), attribute(reader, "name"), attribute(reader, "uuid"));
    }

    private static String attribute(XMLStreamReader reader, String localName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeLocalName(i).equals(localName)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    /** An open element with the identifying attributes the model needs. */
    private record Element(String localName, String id, String name, String uuid) {
    }
}
