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
package org.apache.camel.dsl.jbang.core.commands.generate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.CamelContext;
import org.apache.camel.dataformat.xsdjson.XsdJsonSamples;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Lists the services, ports and operations of a WSDL 1.1 document (SOAP 1.1 and SOAP 1.2 bindings), and scaffolds a
 * YAML route with a cxf: consumer or cxf: producers in PAYLOAD mode, which needs no generated classes.
 * <p/>
 * Exit codes: 0 success, 1 the WSDL cannot be read or scaffolded, 2 invalid options, 3 an unknown service, port or
 * operation, 66 the WSDL file does not exist.
 */
@CommandDefinition(name = "wsdl",
                   description = "List the services, ports and operations of a WSDL, or scaffold a cxf route in PAYLOAD mode",
                   generateHelp = true, sortOptions = true,
                   footer = {
                           "Examples:",
                           "  camel generate wsdl orders.wsdl",
                           "  camel generate wsdl orders.wsdl --json",
                           "  camel generate wsdl orders.wsdl --route=consumer --output=orders.camel.yaml",
                           "  camel generate wsdl orders.wsdl --samples --operation=getOrder",
                           "  camel generate wsdl orders.wsdl --samples --output=samples",
                           "  camel generate wsdl http://localhost:8080/orders?wsdl --route=producer --operation=getOrder" })
public class CodeWsdlGenerator extends CamelCommand {

    public static class RouteCompletionCandidates extends CompletionCandidates {

        public RouteCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("consumer", "producer").iterator();
        }
    }

    @Argument(paramLabel = "wsdl", index = "0", required = true,
              description = "The WSDL: a file or a http(s) URL")
    String wsdl;

    @Option(name = "json", hasValue = false,
            description = "Output the services, ports and operations in JSON Format, with the cxf endpoint options of each port")
    boolean jsonOutput;

    @Option(name = "route", description = "Scaffold a YAML route (consumer or producer) instead of listing the WSDL",
            completer = RouteCompletionCandidates.class)
    String route;

    @Option(name = "output",
            description = "The file to write the route to, or the directory to write the samples to (default: stdout)")
    String output;

    @Option(name = "samples", hasValue = false,
            description = "Write a sample request, response and faults of each operation, as XML and as JSON (the form of"
                          + " the xsdJson data format), and the request in a SOAP envelope with a curl command to send it")
    boolean samples;

    @Option(name = "service", description = "The service (its name), when the WSDL has more than one")
    String service;

    @Option(name = "port", description = "The port (its name), when the service has more than one")
    String port;

    @Option(name = "operation", description = "The operations to list or scaffold (use commas to separate them; default: all)")
    String operation;

    @Override
    public boolean isJsonOutput() {
        return jsonOutput;
    }

    @Override
    public Integer doCall() throws Exception {
        if (route != null && !"consumer".equals(route) && !"producer".equals(route)) {
            return error(CliExitCode.USAGE, "Unknown route: " + route + " (supported: consumer, producer)");
        }
        if (route != null && jsonOutput) {
            return error(CliExitCode.USAGE, "Use --json to list the WSDL, or --route to scaffold a route, not both");
        }
        if (route != null && samples) {
            return error(CliExitCode.USAGE, "Use --route to scaffold a route, or --samples for sample messages, not both");
        }
        if (samples && jsonOutput && output != null) {
            return error(CliExitCode.USAGE, "--output is for the files of the samples, not with --json");
        }
        if (route == null && !samples && output != null) {
            return error(CliExitCode.USAGE, "--output is for the route of --route, or the samples of --samples");
        }

        WsdlModel model;
        try {
            model = WsdlModel.load(wsdl);
        } catch (NoSuchFileException e) {
            return error(CliExitCode.FILE_NOT_FOUND, "WSDL file " + e.getMessage() + " does not exist");
        } catch (WsdlException e) {
            return error(CliExitCode.FAILURE, e.getMessage());
        } catch (IOException e) {
            return error(CliExitCode.FAILURE, "Cannot read the WSDL " + wsdl + ": " + e.getMessage());
        }
        for (String warning : model.warnings()) {
            printer().printWarn(warning);
        }

        // the services, ports and operations chosen with --service, --port and --operation
        List<Service> services = new ArrayList<>();
        for (Service s : model.services()) {
            if (service == null || matches(s.name(), service)) {
                List<Port> ports = new ArrayList<>();
                for (Port p : s.ports()) {
                    if (port == null || matches(p.qname(), port)) {
                        ports.add(p);
                    }
                }
                if (!ports.isEmpty()) {
                    services.add(new Service(s.name(), ports));
                }
            }
        }
        if (service != null && model.services().stream().noneMatch(s -> matches(s.name(), service))) {
            return error(CliExitCode.NOT_FOUND, "Unknown service: " + service + " (the services of the WSDL: "
                                                + String.join(", ", model.services().stream()
                                                        .map(s -> s.name().getLocalPart()).toList())
                                                + ")");
        }
        if (services.isEmpty()) {
            return error(CliExitCode.NOT_FOUND, "Unknown port: " + port + " (the ports of the WSDL: "
                                                + String.join(", ", allPorts(model.services()).stream()
                                                        .map(Port::name).toList())
                                                + ")");
        }
        List<String> operations = operation != null
                ? Arrays.stream(operation.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList()
                : List.of();
        for (String op : operations) {
            boolean known = allPorts(services).stream()
                    .anyMatch(p -> p.operations().stream().anyMatch(o -> o.name().equals(op)));
            if (!known) {
                return error(CliExitCode.NOT_FOUND, "Unknown operation: " + op + " (the operations: "
                                                    + String.join(", ", allPorts(services).stream()
                                                            .flatMap(p -> p.operations().stream())
                                                            .map(Operation::name).distinct().toList())
                                                    + ")");
            }
        }

        if (samples) {
            return samples(model, services, operations);
        }
        if (route == null) {
            if (jsonOutput) {
                JsonOutput.print(printer(), toJson(model, services, operations, null));
            } else {
                printTable(services, operations);
            }
            return CliExitCode.OK;
        }
        return scaffold(services, operations);
    }

    private int scaffold(List<Service> services, List<String> operations) throws IOException {
        List<Port> ports = allPorts(services);
        if (ports.size() > 1) {
            StringBuilder sb = new StringBuilder("The WSDL has more than one port: choose one with");
            for (Port p : ports) {
                sb.append(" --service=").append(p.service().getLocalPart()).append(" --port=").append(p.name())
                        .append(" (SOAP ").append(p.soapVersion()).append(')').append(',');
            }
            sb.setLength(sb.length() - 1);
            return error(CliExitCode.USAGE, sb.toString());
        }
        Port p = ports.get(0);
        WsdlRouteScaffold scaffold = new WsdlRouteScaffold(p, selected(p, operations), wsdlUrl());
        String yaml;
        try {
            yaml = "consumer".equals(route) ? scaffold.consumer() : scaffold.producer();
        } catch (WsdlException e) {
            return error(CliExitCode.FAILURE, e.getMessage());
        }
        for (String warning : scaffold.warnings()) {
            printer().printWarn(warning);
        }
        if (output == null) {
            printer().print(yaml);
        } else {
            Files.writeString(Path.of(output), yaml, StandardCharsets.UTF_8);
        }
        return CliExitCode.OK;
    }

    private int samples(WsdlModel model, List<Service> services, List<String> operations) throws Exception {
        Map<Port, List<Sample>> all = new LinkedHashMap<>();
        List<Port> ports = allPorts(services);
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            XsdJsonSamples xsd;
            try {
                xsd = new XsdJsonSamples(context, wsdlUrl(), schemaReader());
            } catch (RuntimeException e) {
                // the WSDL was read, its types were not
                return error(CliExitCode.FAILURE, "Cannot read the XML schema of the WSDL " + wsdl + ": "
                                                  + rootMessage(e));
            }
            try (xsd) {
                for (Port p : ports) {
                    // the operation names are file names: the port name tells the ports apart
                    String prefix = ports.size() > 1 ? p.name() + "-" : "";
                    WsdlSamples ws = new WsdlSamples(p, selected(p, operations), xsd);
                    all.put(p, ws.samples(prefix));
                    for (String warning : ws.warnings()) {
                        printer().printWarn(warning);
                    }
                }
            }
        }

        if (jsonOutput) {
            JsonOutput.print(printer(), toJson(model, services, operations, all));
        } else if (output != null) {
            Path dir = Path.of(output);
            Files.createDirectories(dir);
            for (List<Sample> list : all.values()) {
                for (Sample sample : list) {
                    for (Map.Entry<String, String> file : files(sample).entrySet()) {
                        Files.writeString(dir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
                        printer().println("Wrote " + dir.resolve(file.getKey()));
                    }
                }
            }
            for (List<Sample> list : all.values()) {
                for (Sample sample : list) {
                    if (sample.curl() != null) {
                        printer().println();
                        printer().println("Send the " + sample.operation() + " request (in " + output + "):");
                        printer().println("  " + sample.curl());
                    }
                }
            }
        } else {
            boolean first = true;
            for (Map.Entry<Port, List<Sample>> entry : all.entrySet()) {
                for (Sample sample : entry.getValue()) {
                    if (!first) {
                        printer().println();
                    }
                    first = false;
                    printSample(entry.getKey(), sample);
                }
            }
        }
        return CliExitCode.OK;
    }

    private void printSample(Port p, Sample sample) {
        String what = "fault".equals(sample.kind())
                ? sample.operation() + " fault " + sample.name() : sample.operation() + " " + sample.kind();
        printer().println("### " + what + ": " + sample.element().getLocalPart() + " (XML)");
        printer().println(sample.xml().strip());
        printer().println();
        printer().println("### " + what + ": " + sample.element().getLocalPart() + " (JSON, xsdJson)");
        printer().println(sample.json().strip());
        if (sample.soap() != null) {
            printer().println();
            printer().println("### " + what + ": SOAP " + p.soapVersion() + " envelope (" + sample.soapFile() + ")");
            printer().println(sample.soap().strip());
            if (sample.curl() != null) {
                printer().println();
                printer().println("### Send it, with the envelope saved as " + sample.soapFile());
                printer().println(sample.curl());
            }
        }
    }

    /**
     * The files of a sample, by name: the XML, the JSON and the SOAP envelope.
     */
    private static Map<String, String> files(Sample sample) {
        Map<String, String> answer = new LinkedHashMap<>();
        answer.put(sample.baseName() + ".xml", sample.xml());
        answer.put(sample.baseName() + ".json", sample.json() + "\n");
        if (sample.soap() != null) {
            answer.put(sample.soapFile(), sample.soap());
        }
        return answer;
    }

    /**
     * Reads the documents of the XML schema of a remote WSDL with the rules of a wsdl:import (the host of the WSDL
     * only, and a redirect only to the same scheme, host and port); a local WSDL only reads files.
     */
    private XsdJsonSamples.DocumentReader schemaReader() {
        String url = wsdlUrl();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        URI root = WsdlModel.toUri(url);
        return uri -> WsdlModel.readDocument(root, uri);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.getMessage();
    }

    /**
     * The wsdlURL of the endpoints: the WSDL as it was given, with file: in front of a file name.
     */
    String wsdlUrl() {
        String lower = wsdl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) {
            return wsdl;
        }
        return "file:" + wsdl;
    }

    private void printTable(List<Service> services, List<String> operations) {
        boolean first = true;
        for (Port p : allPorts(services)) {
            if (!first) {
                printer().println();
            }
            first = false;
            String ns = p.namespace();
            List<String[]> block = List.of(
                    new String[] { "SERVICE", p.service().getLocalPart() },
                    new String[] { "PORT", p.name() },
                    new String[] { "BINDING", p.binding().getLocalPart() },
                    new String[] { "SOAP", p.soapVersion() },
                    new String[] {
                            "STYLE", p.style() + "/" + p.operations().stream().map(Operation::use).findFirst()
                                    .orElse("literal") },
                    new String[] { "ADDRESS", p.address() != null ? WsdlModel.maskedAddress(p.address()) : "" },
                    new String[] { "NAMESPACE", ns });
            for (String[] row : block) {
                printer().println(String.format("%-10s %s", row[0], WsdlModel.printable(row[1])));
            }
            List<Operation> ops = selected(p, operations);
            if (ops.isEmpty()) {
                continue;
            }
            printer().println();
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, ops, Arrays.asList(
                    new Column().header("OPERATION").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(o -> WsdlModel.printable(o.name())),
                    new Column().header("SOAP ACTION").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(o -> WsdlModel.printable(o.soapAction() != null ? o.soapAction() : "")),
                    new Column().header("INPUT").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(o -> WsdlModel.printable(shortName(o.input(), ns))),
                    new Column().header("OUTPUT").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(o -> WsdlModel.printable(o.oneWay() ? "(one-way)" : shortName(o.output(), ns))),
                    new Column().header("FAULTS").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(o -> WsdlModel.printable(
                                    String.join(", ", o.faults().stream().map(Fault::name).toList()))))));
        }
    }

    /**
     * The local name of an element in the namespace of the port, else {namespace}name.
     */
    private static String shortName(QName name, String namespace) {
        if (name == null) {
            return "";
        }
        return name.getNamespaceURI().equals(namespace) ? name.getLocalPart() : name.toString();
    }

    private JsonObject toJson(
            WsdlModel model, List<Service> services, List<String> operations, Map<Port, List<Sample>> samples) {
        JsonObject root = new JsonObject();
        root.put("wsdl", wsdl);
        root.put("targetNamespace", model.targetNamespace());
        JsonArray sa = new JsonArray();
        for (Service s : services) {
            JsonObject so = new JsonObject();
            so.put("name", s.name().getLocalPart());
            so.put("namespace", s.name().getNamespaceURI());
            JsonArray pa = new JsonArray();
            for (Port p : s.ports()) {
                String address = WsdlModel.maskedAddress(p.address());
                JsonObject po = new JsonObject();
                po.put("name", p.name());
                po.put("binding", p.binding().toString());
                po.put("soapVersion", p.soapVersion());
                po.put("style", p.style());
                if (p.transport() != null && !p.transport().isEmpty()) {
                    po.put("transport", p.transport());
                }
                if (address != null) {
                    po.put("address", address);
                }
                po.put("namespace", p.namespace());
                JsonObject cxf = new JsonObject();
                cxf.put("serviceName", p.service().toString());
                cxf.put("portName", p.qname().toString());
                cxf.put("wsdlURL", wsdlUrl());
                if (address != null) {
                    cxf.put("address", address);
                }
                cxf.put("defaultOperationNamespace", p.namespace());
                po.put("cxf", cxf);
                JsonArray oa = new JsonArray();
                for (Operation o : selected(p, operations)) {
                    JsonObject oo = new JsonObject();
                    oo.put("name", o.name());
                    if (o.soapAction() != null) {
                        oo.put("soapAction", o.soapAction());
                    }
                    oo.put("style", o.style());
                    oo.put("use", o.use());
                    oo.put("oneWay", o.oneWay());
                    if (o.input() != null) {
                        oo.put("input", o.input().toString());
                    }
                    if (o.output() != null) {
                        oo.put("output", o.output().toString());
                    }
                    JsonArray fa = new JsonArray();
                    for (Fault f : o.faults()) {
                        JsonObject fo = new JsonObject();
                        fo.put("name", f.name());
                        if (f.element() != null) {
                            fo.put("element", f.element().toString());
                        }
                        fa.add(fo);
                    }
                    oo.put("faults", fa);
                    if (samples != null) {
                        oo.put("samples", samplesJson(samples.getOrDefault(p, List.of()), o.name()));
                    }
                    oa.add(oo);
                }
                po.put("operations", oa);
                pa.add(po);
            }
            so.put("ports", pa);
            sa.add(so);
        }
        root.put("services", sa);
        return root;
    }

    private static JsonArray samplesJson(List<Sample> samples, String operation) {
        JsonArray answer = new JsonArray();
        for (Sample sample : samples) {
            if (!operation.equals(sample.operation())) {
                continue;
            }
            JsonObject so = new JsonObject();
            so.put("kind", sample.kind());
            if ("fault".equals(sample.kind())) {
                so.put("fault", sample.name());
            }
            so.put("element", sample.element().toString());
            so.put("xml", sample.xml());
            so.put("json", sample.json());
            if (sample.soap() != null) {
                so.put("soap", sample.soap());
            }
            if (sample.curl() != null) {
                so.put("curl", sample.curl());
            }
            answer.add(so);
        }
        return answer;
    }

    private static List<Operation> selected(Port p, List<String> operations) {
        if (operations.isEmpty()) {
            return p.operations();
        }
        return p.operations().stream().filter(o -> operations.contains(o.name())).toList();
    }

    private static List<Port> allPorts(List<Service> services) {
        return services.stream().flatMap(s -> s.ports().stream()).toList();
    }

    /**
     * Whether the name of the option matches the name: its local name, or {namespace}name.
     */
    private static boolean matches(QName name, String option) {
        return option.equals(name.getLocalPart()) || option.equals(name.toString());
    }

    private int error(int code, String message) {
        // the message may have values of a remote WSDL
        message = WsdlModel.printable(message);
        printer().printErr(message);
        if (jsonOutput) {
            JsonOutput.printError(printer(), code, message);
        }
        return code;
    }
}
