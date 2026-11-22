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
package org.apache.camel.dsl.jbang.wsdl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.apache.camel.dataformat.xsdjson.XsdJsonSamples;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Fault;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Operation;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Port;

/**
 * The sample messages of the operations of a port: the request, the response and the faults as XML and as JSON in the
 * form of the xsdJson data format, and the request in a SOAP envelope with a curl command to send it.
 * <p>
 * The names of the operations and faults go into file names and the address and SOAP action into a shell command, so
 * they must be NCNames and plain URIs (no quote, space or brace); a message whose values are not is left out with a
 * warning, as a hostile WSDL could otherwise put a command into the curl line.
 */
public final class WsdlSamples {

    public static final String SOAP11_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String SOAP12_ENV = "http://www.w3.org/2003/05/soap-envelope";

    /**
     * The characters of an address or a SOAP action that can go into a single-quoted shell argument as they are.
     */
    private static final Pattern SAFE_URI = Pattern.compile("[\\p{L}\\p{N}\\-._~:/?#\\[\\]@!$&*+,;=%]*");

    /**
     * A sample message of an operation.
     *
     * @param prefix    the prefix of the file names (or empty)
     * @param operation the operation
     * @param kind      request, response or fault
     * @param name      the name of the fault, else the operation name
     * @param element   the element of the message
     * @param xml       the XML document
     * @param json      the JSON, as the xsdJson data format reads and writes it
     * @param soap      the request in a SOAP envelope, else null
     * @param curl      the curl command that sends the SOAP envelope (from the file {@link #soapFile()}), else null
     */
    public record Sample(
            String prefix, String operation, String kind, String name, QName element, String xml, String json, String soap,
            String curl) {

        /**
         * The base name of the files of this sample, such as getOrder-request or getOrder-fault-orderNotFound.
         */
        public String baseName() {
            return prefix + ("fault".equals(kind) ? operation + "-fault-" + name : operation + "-" + kind);
        }

        public String soapFile() {
            return baseName() + ".soap.xml";
        }
    }

    private final Port port;
    private final List<Operation> operations;
    private final XsdJsonSamples samples;
    private final List<String> warnings = new ArrayList<>();

    public WsdlSamples(Port port, List<Operation> operations, XsdJsonSamples samples) {
        this.port = port;
        this.operations = operations;
        this.samples = samples;
    }

    public List<String> warnings() {
        return warnings;
    }

    /**
     * The samples of the operations, in the order of the operations: the request, the response and the faults.
     *
     * @param prefix the prefix of the file names, such as the port name when there is more than one port (or empty)
     */
    public List<Sample> samples(String prefix) {
        List<Sample> answer = new ArrayList<>();
        if (!port.isDocument()) {
            warnings.add("The port " + port.name() + " uses the rpc style, which has no samples (only document style)");
            return answer;
        }
        String address = address();
        for (Operation op : operations) {
            if (!WsdlModel.isNcName(op.name())) {
                warnings.add("The operation '" + WsdlModel.printable(op.name()) + "' is not a valid NCName: no samples");
                continue;
            }
            String operation = op.name();
            if (op.input() != null) {
                Sample request = sample(prefix, operation, "request", op.name(), op.input());
                if (request != null) {
                    String soap = envelope(request.xml());
                    String curl = curl(op, address, request.soapFile());
                    answer.add(new Sample(
                            prefix, operation, "request", op.name(), op.input(), request.xml(),
                            request.json(), soap, curl));
                }
            }
            if (op.output() != null) {
                Sample response = sample(prefix, operation, "response", op.name(), op.output());
                if (response != null) {
                    answer.add(response);
                }
            }
            for (Fault fault : op.faults()) {
                if (fault.element() == null) {
                    continue;
                }
                if (!WsdlModel.isNcName(fault.name())) {
                    warnings.add("The fault '" + WsdlModel.printable(fault.name()) + "' of " + op.name()
                                 + " is not a valid NCName: no samples");
                    continue;
                }
                Sample sample = sample(prefix, operation, "fault", fault.name(), fault.element());
                if (sample != null) {
                    answer.add(sample);
                }
            }
        }
        return answer;
    }

    private Sample sample(String prefix, String operation, String kind, String name, QName element) {
        try {
            return new Sample(
                    prefix, operation, kind, name, element, samples.xml(element.toString()),
                    samples.json(element.toString()), null, null);
        } catch (IllegalArgumentException e) {
            warnings.add("No sample of the " + kind + " " + element.getLocalPart() + " of " + operation + ": "
                         + WsdlModel.printable(e.getMessage()));
            return null;
        }
    }

    /**
     * The request in a SOAP envelope of the SOAP version of the port.
     */
    public String envelope(String xml) {
        String body = xml;
        if (body.startsWith("<?xml")) {
            int end = body.indexOf("?>");
            body = body.substring(end + 2);
        }
        String ns = "1.2".equals(port.soapVersion()) ? SOAP12_ENV : SOAP11_ENV;
        StringBuilder sb = new StringBuilder();
        sb.append("<soapenv:Envelope xmlns:soapenv=\"").append(ns).append("\">\n");
        sb.append("  <soapenv:Body>\n");
        for (String line : body.strip().split("\n")) {
            sb.append("    ").append(line).append('\n');
        }
        sb.append("  </soapenv:Body>\n");
        sb.append("</soapenv:Envelope>\n");
        return sb.toString();
    }

    /**
     * The curl command that posts the SOAP envelope in the file to the address of the port, with the SOAP action in the
     * SOAPAction header (SOAP 1.1) or the action parameter of the content type (SOAP 1.2).
     */
    private String curl(Operation op, String address, String file) {
        if (address == null) {
            return null;
        }
        String action = op.soapAction() != null ? op.soapAction() : "";
        if (!SAFE_URI.matcher(action).matches()) {
            warnings.add("The SOAP action of " + op.name() + " has characters that are not put into a curl command");
            return null;
        }
        StringBuilder sb = new StringBuilder("curl -X POST");
        if ("1.2".equals(port.soapVersion())) {
            sb.append(" -H 'Content-Type: application/soap+xml; charset=utf-8")
                    .append(action.isEmpty() ? "" : "; action=\"" + action + "\"").append('\'');
        } else {
            sb.append(" -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: \"").append(action).append("\"'");
        }
        sb.append(" --data-binary @").append(file).append(" '").append(address).append('\'');
        return sb.toString();
    }

    /**
     * The address of the port without the user info and the query, or null when it has none or it has characters that
     * cannot go into the curl command.
     */
    private String address() {
        String raw = port.address();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        if (uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) {
            return null;
        }
        String answer = scheme + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                        + (uri.getRawPath() != null ? uri.getRawPath() : "");
        if (!SAFE_URI.matcher(answer).matches()) {
            warnings.add("The address of the port " + port.name() + " has characters that are not put into a curl command");
            return null;
        }
        return answer;
    }
}
