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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders streams and error documents in the shape a real agent of the dialect writes them: the 2.5 documents compact
 * as cppagent 2.8 writes them, the 1.x documents line by line with the vendor namespace as the Mazak agents write them.
 * Observations are grouped by device, component and category, as agents do, not in sequence order.
 */
final class StubDocuments {

    /** One observation in the stub's buffer. A null value is UNAVAILABLE; a condition carries its level. */
    record Observation(
            long sequence, StubDeviceModel.Item item, String value, Map<String, String> entries, String level,
            String nativeCode, String nativeSeverity, String qualifier, Map<String, String> attributes,
            Instant timestamp) {
    }

    /** The header values of a streams document. */
    record Window(long instanceId, int bufferSize, long nextSequence, long firstSequence, long lastSequence) {
    }

    private final StubDialect dialect;
    private final String newline;

    StubDocuments(StubDialect dialect) {
        this.dialect = dialect;
        this.newline = dialect.modern() ? "" : "\n";
    }

    String streams(Window window, List<Observation> observations, boolean part) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (dialect.modern() && !part) {
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"/styles/styles.xsl\"?>");
        }
        String namespace = "urn:mtconnect.org:MTConnectStreams:" + dialect.schema;
        xml.append("<MTConnectStreams xmlns:m=\"").append(namespace).append("\" xmlns=\"").append(namespace)
                .append("\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        if (dialect.vendorNamespace != null) {
            xml.append(" xmlns:x=\"").append(dialect.vendorNamespace).append('"');
        }
        xml.append(" xsi:schemaLocation=\"").append(namespace).append(" /schemas/MTConnectStreams_")
                .append(dialect.schema).append(".xsd\">").append(newline);
        xml.append(indent(1)).append("<Header").append(headerAttributes(window.instanceId(), window.bufferSize()))
                .append(" nextSequence=\"").append(window.nextSequence()).append("\" firstSequence=\"")
                .append(window.firstSequence()).append("\" lastSequence=\"").append(window.lastSequence())
                .append("\"/>").append(newline);
        if (observations.isEmpty()) {
            xml.append(indent(1)).append("<Streams/>").append(newline);
        } else {
            xml.append(indent(1)).append("<Streams>").append(newline);
            appendDevices(xml, observations);
            xml.append(indent(1)).append("</Streams>").append(newline);
        }
        return xml.append("</MTConnectStreams>").append(newline).toString();
    }

    String error(long instanceId, int bufferSize, String code, String message) {
        String namespace = "urn:mtconnect.org:MTConnectError:" + dialect.schema;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<MTConnectError xmlns:m=\"" + namespace + "\" xmlns=\""
               + namespace + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\""
               + namespace + " /schemas/MTConnectError_" + dialect.schema + ".xsd\">" + newline + indent(1) + "<Header"
               + headerAttributes(instanceId, bufferSize) + "/>" + newline + indent(1) + "<Errors>" + newline
               + indent(2) + "<Error errorCode=\"" + code + "\">" + escape(message) + "</Error>" + newline + indent(1)
               + "</Errors>" + newline + "</MTConnectError>" + newline;
    }

    private String headerAttributes(long instanceId, int bufferSize) {
        String time = dialect.modern() ? "2026-10-27T16:13:18.285143Z" : "2026-10-27T16:13:04Z";
        StringBuilder header = new StringBuilder(" creationTime=\"").append(time)
                .append("\" sender=\"agent-host\" instanceId=\"").append(instanceId).append("\" version=\"")
                .append(dialect.version).append('"');
        if (dialect.modern()) {
            header.append(" deviceModelChangeTime=\"2026-10-27T15:02:22.000784Z\"");
        }
        return header.append(" bufferSize=\"").append(bufferSize).append('"').toString();
    }

    private void appendDevices(StringBuilder xml, List<Observation> observations) {
        Map<String, Map<String, List<Observation>>> byDevice = new LinkedHashMap<>();
        List<Observation> grouped = new ArrayList<>(observations);
        grouped.sort(Comparator.comparing((Observation o) -> o.item().deviceUuid())
                .thenComparing(o -> o.item().componentId()));
        for (Observation observation : grouped) {
            byDevice.computeIfAbsent(observation.item().deviceUuid(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(observation.item().componentId(), key -> new ArrayList<>()).add(observation);
        }
        for (Map<String, List<Observation>> components : byDevice.values()) {
            StubDeviceModel.Item first = components.values().iterator().next().get(0).item();
            xml.append(indent(2)).append("<DeviceStream name=\"").append(first.deviceName()).append("\" uuid=\"")
                    .append(first.deviceUuid()).append("\">").append(newline);
            for (List<Observation> component : components.values()) {
                StubDeviceModel.Item item = component.get(0).item();
                xml.append(indent(3)).append("<ComponentStream component=\"").append(item.componentType()).append('"');
                if (item.componentName() != null) {
                    xml.append(" name=\"").append(item.componentName()).append('"');
                }
                xml.append(" componentId=\"").append(item.componentId()).append("\">").append(newline);
                appendContainer(xml, "Samples", "SAMPLE", component);
                appendContainer(xml, "Events", "EVENT", component);
                appendContainer(xml, "Condition", "CONDITION", component);
                xml.append(indent(3)).append("</ComponentStream>").append(newline);
            }
            xml.append(indent(2)).append("</DeviceStream>").append(newline);
        }
    }

    private void appendContainer(StringBuilder xml, String container, String category, List<Observation> component) {
        List<Observation> selected = component.stream().filter(o -> o.item().category().equals(category)).toList();
        if (selected.isEmpty()) {
            return;
        }
        xml.append(indent(4)).append('<').append(container).append('>').append(newline);
        for (Observation observation : selected) {
            xml.append(indent(5)).append(observation(observation)).append(newline);
        }
        xml.append(indent(4)).append("</").append(container).append('>').append(newline);
    }

    private static String observation(Observation observation) {
        StubDeviceModel.Item item = observation.item();
        String element = item.condition() ? observation.level() : item.elementName();
        StringBuilder xml = new StringBuilder("<").append(element);
        if (observation.entries() != null) {
            xml.append(" count=\"").append(observation.entries().size()).append('"');
        }
        xml.append(" dataItemId=\"").append(item.id()).append('"');
        if (item.name() != null) {
            xml.append(" name=\"").append(item.name()).append('"');
        }
        if (observation.nativeCode() != null) {
            xml.append(" nativeCode=\"").append(observation.nativeCode()).append('"');
        }
        if (observation.nativeSeverity() != null) {
            xml.append(" nativeSeverity=\"").append(observation.nativeSeverity()).append('"');
        }
        if (observation.qualifier() != null) {
            xml.append(" qualifier=\"").append(observation.qualifier()).append('"');
        }
        xml.append(" sequence=\"").append(observation.sequence()).append('"');
        if (item.subType() != null) {
            xml.append(" subType=\"").append(item.subType()).append('"');
        }
        xml.append(" timestamp=\"").append(observation.timestamp()).append('"');
        observation.attributes().forEach((name, value) -> xml.append(' ').append(name).append("=\"").append(escape(value))
                .append('"'));
        if (item.condition()) {
            xml.append(" type=\"").append(item.type()).append('"');
            if (observation.value() == null) {
                return xml.append("/>").toString();
            }
            return xml.append('>').append(escape(observation.value())).append("</").append(element).append('>')
                    .toString();
        }
        xml.append('>');
        if (observation.entries() != null) {
            observation.entries().forEach((key, value) -> xml.append("<Entry key=\"").append(escape(key)).append("\">")
                    .append(escape(value)).append("</Entry>"));
        } else {
            xml.append(observation.value() == null ? "UNAVAILABLE" : escape(observation.value()));
        }
        return xml.append("</").append(element).append('>').toString();
    }

    private String indent(int depth) {
        return dialect.modern() ? "" : "  ".repeat(depth);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
