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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import org.apache.camel.dataformat.xsdjson.XsdJsonSamples;
import org.apache.camel.dsl.jbang.core.common.ScratchContextHelper;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Fault;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Operation;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Port;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Service;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.WsdlException;
import org.apache.camel.dsl.jbang.wsdl.WsdlRouteScaffold;
import org.apache.camel.dsl.jbang.wsdl.WsdlSamples;
import org.apache.camel.dsl.jbang.wsdl.WsdlSamples.Sample;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * MCP Tools for SOAP services: what camel generate wsdl does, from the content of a WSDL. The WSDL is given as content,
 * so the tools read no file and call no host: a wsdl:import or an xs:import of the WSDL is not followed (it is listed
 * as a warning, or the samples of its elements are left out).
 */
@McpSecured
@ApplicationScoped
public class WsdlTools {

    /**
     * The location of the WSDL content for the XML schema loader; only this document is read.
     */
    private static final String CONTENT = "content:service.wsdl";

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List the services, ports and operations of a WSDL 1.1 (SOAP 1.1 or 1.2), with the cxf endpoint"
                        + " options of each port (serviceName, portName, address, defaultOperationNamespace) and the"
                        + " request, response and fault elements of each operation. With samples=true, each operation"
                        + " also has a sample request, response and faults, as XML and as JSON in the form of the"
                        + " xsdJson data format, and the request in a SOAP envelope with a curl command."
                        + " Imports of the WSDL are not followed.")
    public InspectResult camel_wsdl_inspect(
            @ToolArg(description = "The WSDL document (XML content)") String wsdl,
            @ToolArg(description = "The operations to list (comma separated; default: all)",
                     required = false) String operations,
            @ToolArg(description = "Whether to add sample messages to each operation (default: false)",
                     required = false) Boolean samples) {

        WsdlModel model = parse(wsdl);
        List<String> ops = names(operations);
        List<String> warnings = new ArrayList<>(model.warnings());
        Map<Port, List<Sample>> all = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(samples)) {
            all = samples(wsdl, model.services(), ops, warnings);
        }

        List<PortInfo> ports = new ArrayList<>();
        for (Service s : model.services()) {
            for (Port p : s.ports()) {
                List<OperationInfo> list = new ArrayList<>();
                for (Operation o : selected(p, ops)) {
                    List<FaultInfo> faults = new ArrayList<>();
                    for (Fault f : o.faults()) {
                        faults.add(new FaultInfo(f.name(), f.element() != null ? f.element().toString() : null));
                    }
                    List<SampleInfo> sampleInfos = null;
                    if (all.containsKey(p)) {
                        sampleInfos = new ArrayList<>();
                        for (Sample sample : all.get(p)) {
                            if (sample.operation().equals(o.name())) {
                                sampleInfos.add(new SampleInfo(
                                        sample.kind(), "fault".equals(sample.kind()) ? sample.name() : null,
                                        sample.element().toString(), sample.xml(), sample.json(), sample.soap(),
                                        sample.curl()));
                            }
                        }
                    }
                    list.add(new OperationInfo(
                            o.name(), o.soapAction(), o.style(), o.oneWay(),
                            o.input() != null ? o.input().toString() : null,
                            o.output() != null ? o.output().toString() : null, faults, sampleInfos));
                }
                String address = WsdlModel.maskedAddress(p.address());
                Map<String, String> cxf = new LinkedHashMap<>();
                cxf.put("serviceName", p.service().toString());
                cxf.put("portName", p.qname().toString());
                if (address != null) {
                    cxf.put("address", address);
                }
                cxf.put("defaultOperationNamespace", p.namespace());
                ports.add(new PortInfo(
                        s.name().getLocalPart(), p.name(), p.binding().toString(), p.soapVersion(), p.style(), address,
                        p.namespace(), cxf, list));
            }
        }
        return new InspectResult(model.targetNamespace(), ports, warnings);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Scaffold a Camel YAML route for a port of a WSDL, in PAYLOAD mode with no generated classes."
                        + " route=consumer is a SOAP service: a cxf consumer (with schema validation) whose address is"
                        + " the path of the WSDL address, such as cxf:/orders, served under /services by Camel Main,"
                        + " Spring Boot and Quarkus, with a choice of one when per operation that returns a placeholder"
                        + " response. route=producer is a SOAP client: a direct:<operation> route per operation that"
                        + " calls the service with the request element as the body.")
    public ScaffoldResult camel_wsdl_scaffold(
            @ToolArg(description = "The WSDL document (XML content)") String wsdl,
            @ToolArg(description = "consumer (a SOAP service) or producer (a SOAP client)") String route,
            @ToolArg(description = "The wsdlURL of the endpoints, where the route finds the WSDL at runtime"
                                   + " (default: classpath:service.wsdl)",
                     required = false) String wsdlUrl,
            @ToolArg(description = "The service (its name), when the WSDL has more than one", required = false) String service,
            @ToolArg(description = "The port (its name), when the service has more than one", required = false) String port,
            @ToolArg(description = "The operations to scaffold (comma separated; default: all)",
                     required = false) String operations) {

        String kind = route == null ? "" : route.strip().toLowerCase(Locale.ROOT);
        if (!"consumer".equals(kind) && !"producer".equals(kind)) {
            throw new ToolCallException("'route' must be 'consumer' or 'producer', got: " + route, null);
        }
        String url = wsdlUrl == null || wsdlUrl.isBlank() ? "classpath:service.wsdl" : wsdlUrl.strip();
        WsdlModel model = parse(wsdl);

        List<Port> ports = new ArrayList<>();
        for (Service s : model.services()) {
            if (service != null && !service.isBlank() && !matches(s.name().getLocalPart(), s.name().toString(), service)) {
                continue;
            }
            for (Port p : s.ports()) {
                if (port == null || port.isBlank() || matches(p.name(), p.qname().toString(), port)) {
                    ports.add(p);
                }
            }
        }
        if (ports.isEmpty()) {
            throw new ToolCallException("No port matches the service and port given", null);
        }
        if (ports.size() > 1) {
            StringBuilder sb = new StringBuilder("The WSDL has more than one port; choose one with service and port:");
            for (Port p : ports) {
                sb.append(" service=").append(p.service().getLocalPart()).append(" port=").append(p.name())
                        .append(" (SOAP ").append(p.soapVersion()).append(')');
            }
            throw new ToolCallException(sb.toString(), null);
        }
        Port p = ports.get(0);
        List<String> ops = names(operations);
        List<Operation> selected = selected(p, ops);
        for (String op : ops) {
            if (selected.stream().noneMatch(o -> o.name().equals(op))) {
                throw new ToolCallException(
                        "Unknown operation: " + op + " (the operations: "
                                            + String.join(", ", p.operations().stream().map(Operation::name).toList())
                                            + ")",
                        null);
            }
        }
        WsdlRouteScaffold scaffold = new WsdlRouteScaffold(p, selected, url);
        String yaml;
        try {
            yaml = "consumer".equals(kind) ? scaffold.consumer() : scaffold.producer();
        } catch (WsdlException e) {
            throw new ToolCallException(e.getMessage(), null);
        }
        List<String> warnings = new ArrayList<>(model.warnings());
        warnings.addAll(scaffold.warnings());
        String explanation = "consumer".equals(kind)
                ? "The consumer is served on the HTTP server of the runtime under /services (Camel Main needs"
                  + " camel-cxf-platform-http, which camel run adds). Replace the placeholder response of each operation:"
                  + " until then the schema validation answers with a SOAP fault that names the first missing element."
                : "Send the request element (XML) to direct:<operation>. The route removes the Camel and CXF operation"
                  + " headers first, so a caller cannot choose another operation or address.";
        return new ScaffoldResult(
                yaml, kind, p.service().getLocalPart(), p.name(), p.soapVersion(), url, explanation,
                warnings);
    }

    private static WsdlModel parse(String wsdl) {
        if (wsdl == null || wsdl.isBlank()) {
            throw new ToolCallException("'wsdl' parameter is required and must not be blank", null);
        }
        try {
            return WsdlModel.parse(wsdl.getBytes(StandardCharsets.UTF_8));
        } catch (WsdlException e) {
            throw new ToolCallException("Cannot read the WSDL: " + e.getMessage(), null);
        }
    }

    private static Map<Port, List<Sample>> samples(
            String wsdl, List<Service> services, List<String> operations, List<String> warnings) {
        byte[] content = wsdl.getBytes(StandardCharsets.UTF_8);
        XsdJsonSamples.DocumentReader reader = uri -> {
            if (CONTENT.equals(uri)) {
                return content;
            }
            throw new IOException(
                    "The document " + WsdlModel.printable(uri)
                                  + " is not read, as the WSDL is given as content");
        };
        Map<Port, List<Sample>> answer = new LinkedHashMap<>();
        try (DefaultCamelContext context = ScratchContextHelper.withoutCliConnector(new DefaultCamelContext())) {
            context.start();
            XsdJsonSamples xsd;
            try {
                xsd = new XsdJsonSamples(context, CONTENT, reader);
            } catch (RuntimeException e) {
                warnings.add("No samples: the XML schema of the WSDL cannot be read: " + WsdlModel.printable(e.getMessage()));
                return answer;
            }
            try (xsd) {
                for (Service s : services) {
                    for (Port p : s.ports()) {
                        WsdlSamples ws = new WsdlSamples(p, selected(p, operations), xsd);
                        answer.put(p, ws.samples(""));
                        warnings.addAll(ws.warnings());
                    }
                }
            }
        } catch (IOException e) {
            // closing the CamelContext of the samples
            warnings.add("Cannot stop the CamelContext of the samples: " + e.getMessage());
        }
        return answer;
    }

    private static List<String> names(String operations) {
        if (operations == null || operations.isBlank()) {
            return List.of();
        }
        return Arrays.stream(operations.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toList();
    }

    private static List<Operation> selected(Port p, List<String> operations) {
        if (operations.isEmpty()) {
            return p.operations();
        }
        return p.operations().stream().filter(o -> operations.contains(o.name())).toList();
    }

    private static boolean matches(String localName, String qname, String option) {
        String o = option.strip();
        return o.equals(localName) || o.equals(qname);
    }

    // -- Result records --

    public record InspectResult(String targetNamespace, List<PortInfo> ports, List<String> warnings) {
    }

    public record PortInfo(
            String service, String port, String binding, String soapVersion, String style, String address,
            String namespace, Map<String, String> cxf, List<OperationInfo> operations) {
    }

    public record OperationInfo(
            String name, String soapAction, String style, boolean oneWay, String input, String output,
            List<FaultInfo> faults, List<SampleInfo> samples) {
    }

    public record FaultInfo(String name, String element) {
    }

    /**
     * A sample message of an operation.
     *
     * @param kind    request, response or fault
     * @param fault   the name of the fault, for a fault
     * @param element the element of the message
     * @param xml     the XML document
     * @param json    the JSON in the form of the xsdJson data format
     * @param soap    the request in a SOAP envelope (request only)
     * @param curl    a curl command that posts the envelope, saved as a file, to the address of the port (request only)
     */
    public record SampleInfo(
            String kind, String fault, String element, String xml, String json, String soap, String curl) {
    }

    public record ScaffoldResult(
            String yaml, String route, String service, String port, String soapVersion, String wsdlUrl,
            String explanation, List<String> warnings) {
    }
}
