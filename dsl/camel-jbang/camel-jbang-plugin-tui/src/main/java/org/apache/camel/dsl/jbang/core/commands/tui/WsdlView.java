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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import org.apache.camel.dsl.jbang.wsdl.WsdlModel;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Fault;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Operation;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Port;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Service;

/**
 * The WSDL of a cxf endpoint, as markdown for the Endpoints tab: the ports of its services and the request, response
 * and fault elements of each operation.
 * <p>
 * The WSDL is the wsdlURL of the endpoint: a file: or classpath: resource is looked up in the folder of the integration
 * (and its src/main/resources), and an http(s) URL is read with the rules of camel generate wsdl (imports only from its
 * host, a redirect only to the same scheme, host and port). It is read once, on a thread of its own, so a slow server
 * does not block the screen.
 */
final class WsdlView {

    static final String LOADING = "*Reading the WSDL...*";

    private final Map<String, String> markdown = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    /**
     * The markdown of the WSDL at the location, or {@link #LOADING} while it is read.
     */
    String markdown(String wsdlUrl, String location) {
        String md = markdown.get(location);
        if (md != null) {
            return md;
        }
        if (loading.putIfAbsent(location, Boolean.TRUE) == null) {
            Thread.ofVirtual().name("CamelTuiWsdl").start(() -> {
                try {
                    markdown.put(location, toMarkdown(wsdlUrl, location, WsdlModel.load(location)));
                } catch (Exception e) {
                    markdown.put(location, "*Cannot read the WSDL " + escape(wsdlUrl) + ":* "
                                           + escape(e.getMessage() != null ? e.getMessage() : e.toString()));
                } finally {
                    loading.remove(location);
                }
            });
        }
        return LOADING;
    }

    /**
     * Where to read the WSDL of a wsdlURL from: an http(s) URL as it is, a file: or classpath: resource (or a plain
     * name) as a file in the folder of the integration, or its src/main/resources; null when it is not found.
     */
    static String location(String wsdlUrl, String directory) {
        if (wsdlUrl == null || wsdlUrl.isBlank()) {
            return null;
        }
        String url = wsdlUrl.strip();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return url;
        }
        String name;
        if (lower.startsWith("classpath:")) {
            name = url.substring("classpath:".length());
        } else if (lower.startsWith("file:")) {
            name = url.substring("file:".length());
        } else if (url.contains(":") && !url.startsWith("/")) {
            // another scheme, such as a bean reference or a property placeholder that is not resolved
            return null;
        } else {
            name = url;
        }
        while (name.startsWith("//")) {
            name = name.substring(1);
        }
        Path path = Path.of(name);
        if (path.isAbsolute()) {
            return Files.isRegularFile(path) ? path.toString() : null;
        }
        Path base = directory != null ? Path.of(directory) : Path.of("");
        for (Path candidate : new Path[] { base.resolve(name), base.resolve("src/main/resources").resolve(name) }) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return null;
    }

    static String toMarkdown(String wsdlUrl, String location, WsdlModel model) {
        StringBuilder md = new StringBuilder();
        md.append("## WSDL ").append(escape(wsdlUrl)).append("\n\n");
        for (Service service : model.services()) {
            for (Port port : service.ports()) {
                String ns = port.namespace();
                md.append("### ").append(escape(service.name().getLocalPart())).append(" · port ")
                        .append(escape(port.name())).append("\n\n");
                md.append("SOAP ").append(escape(port.soapVersion())).append(", ").append(escape(port.style()))
                        .append(" style");
                String address = WsdlModel.maskedAddress(port.address());
                if (address != null) {
                    md.append(", at `").append(code(address)).append('`');
                }
                md.append("\n\n");
                if (port.operations().isEmpty()) {
                    md.append("*No operations*\n\n");
                    continue;
                }
                md.append("| Operation | Request | Response | Faults |\n|---|---|---|---|\n");
                for (Operation op : port.operations()) {
                    StringBuilder faults = new StringBuilder();
                    for (Fault f : op.faults()) {
                        if (!faults.isEmpty()) {
                            faults.append(", ");
                        }
                        faults.append(escape(f.name()));
                        if (f.element() != null && !f.element().getLocalPart().equals(f.name())) {
                            faults.append(" (").append(escape(name(f.element(), ns))).append(')');
                        }
                    }
                    md.append("| **").append(escape(op.name())).append("** | ")
                            .append(escape(name(op.input(), ns))).append(" | ")
                            .append(op.oneWay() ? "*one-way*" : escape(name(op.output(), ns))).append(" | ")
                            .append(faults).append(" |\n");
                }
                md.append('\n');
            }
        }
        for (String warning : model.warnings()) {
            md.append("- ").append(escape(warning)).append('\n');
        }
        if (!model.warnings().isEmpty()) {
            md.append('\n');
        }
        md.append("Sample messages: `camel generate wsdl ").append(code(location)).append(" --samples`\n");
        return md.toString();
    }

    /**
     * The local name of an element in the namespace of the port, else {namespace}name.
     */
    private static String name(QName name, String namespace) {
        if (name == null) {
            return "";
        }
        return name.getNamespaceURI().equals(namespace) ? name.getLocalPart() : name.toString();
    }

    /**
     * A value of the WSDL as markdown text: printable, on one line, and without a character that would end a table cell
     * or start a code span.
     */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        // printable turns a line break or other control character into ?
        return WsdlModel.printable(text).replace("\\", "\\\\").replace("|", "\\|").replace('`', '\'');
    }

    /**
     * A value of the WSDL in an inline code span: printable, without backticks.
     */
    private static String code(String text) {
        return WsdlModel.printable(text).replace('`', '\'').replace('|', '/');
    }
}
