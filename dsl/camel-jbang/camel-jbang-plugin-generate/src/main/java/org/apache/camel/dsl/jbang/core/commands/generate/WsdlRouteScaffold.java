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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.apache.camel.dsl.jbang.core.commands.generate.WsdlModel.Operation;
import org.apache.camel.dsl.jbang.core.commands.generate.WsdlModel.Port;
import org.apache.camel.dsl.jbang.core.commands.generate.WsdlModel.WsdlException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Scaffolds a YAML route with a cxf: consumer or cxf: producers in PAYLOAD mode for a port of a WSDL, with no generated
 * classes. The YAML is written with a YAML writer, and every name from the WSDL that goes into it is an NCName, and
 * every namespace a plain URI, so a hostile WSDL cannot add YAML, a simple expression or a property placeholder.
 */
final class WsdlRouteScaffold {

    /**
     * The characters of a namespace or an address that can go into an endpoint parameter, a constant and an XML
     * attribute as they are: no brace (a property placeholder), quote, parenthesis (RAW), angle bracket or space.
     */
    private static final Pattern SAFE_URI = Pattern.compile("[\\p{L}\\p{N}\\-._~:/?#\\[\\]@!$&*+,;=%]*");

    /**
     * The headers that decide which operation and which address the cxf producer calls: they are removed before the
     * call, so a caller of the route (or a consumer that copied them from a request) cannot steer it.
     */
    static final String PRODUCER_REMOVE_HEADERS = "Camel.*|operationName|operationNamespace|RequestContext|ResponseContext";

    private static final int DEFAULT_PORT = 8080;

    private final Port port;
    private final List<Operation> operations;
    private final String wsdlUrl;
    private final List<String> warnings = new ArrayList<>();

    /**
     * @param port       the port
     * @param operations the operations of the port to scaffold
     * @param wsdlUrl    the wsdlURL of the endpoints
     */
    WsdlRouteScaffold(Port port, List<Operation> operations, String wsdlUrl) {
        this.port = port;
        this.operations = operations;
        this.wsdlUrl = wsdlUrl;
    }

    /**
     * What the scaffold changed from the WSDL, such as the query of an address that it left out.
     */
    List<String> warnings() {
        return warnings;
    }

    /**
     * A route with a cxf: consumer for the port, with a choice of one when per operation. The address of the consumer
     * is the path of the address of the port: Camel Main (camel-cxf-platform-http), Spring Boot and Quarkus all serve
     * it on their HTTP server, under the path of their CXF services (default /services).
     */
    String consumer() throws WsdlException {
        check();
        String path = address().getRawPath();
        String uri = "cxf:" + path;

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("wsdlURL", wsdlUrl);
        parameters.put("serviceName", port.service().toString());
        parameters.put("portName", port.qname().toString());
        parameters.put("dataFormat", "PAYLOAD");
        parameters.put("schemaValidationEnabled", true);

        List<Object> whens = new ArrayList<>();
        for (Operation op : operations) {
            List<Object> steps = new ArrayList<>();
            steps.add(Map.of("log", Map.of("message", op.name() + " request: ${body}")));
            if (op.output() != null) {
                Map<String, Object> setBody = new LinkedHashMap<>();
                setBody.put("note", "TODO: build the " + op.output().getLocalPart() + " response");
                setBody.put("expression", constant(placeholder(op.output())));
                steps.add(Map.of("setBody", setBody));
            }
            Map<String, Object> when = new LinkedHashMap<>();
            when.put("expression", simple("${header.CamelCxfOperationName} == '" + op.name() + "'"));
            when.put("steps", steps);
            whens.add(when);
        }

        List<Object> steps = new ArrayList<>();
        steps.add(Map.of("convertBodyTo", Map.of("type", "java.lang.String")));
        steps.add(Map.of("choice", Map.of("when", whens)));

        Map<String, Object> from = new LinkedHashMap<>();
        from.put("uri", uri);
        from.put("parameters", parameters);
        from.put("steps", steps);

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("id", port.service().getLocalPart());
        route.put("description", "The " + port.service().getLocalPart() + " SOAP service, port " + port.name());
        route.put("from", from);

        StringBuilder sb = header("consumer");
        sb.append("#\n");
        sb.append("# Each operation returns a placeholder response: replace it with the response element.\n");
        sb.append("# The request and the response are validated against the schema of the WSDL (schemaValidationEnabled):\n");
        sb.append("# until it is filled in, the placeholder gives a SOAP fault that names the first element it misses.\n");
        sb.append("# The address is the path ").append(path)
                .append(" of the WSDL address: the HTTP server of the runtime serves it under /services,\n");
        sb.append("# such as http://localhost:8080/services").append(path).append(" with the default port.\n");
        return sb + dump(List.of(Map.of("route", route)));
    }

    /**
     * A route with a cxf: producer per operation, called with direct:operation and the request element as the body.
     */
    String producer() throws WsdlException {
        check();
        URI address = address();
        String uri = "cxf://" + address;

        List<Object> routes = new ArrayList<>();
        for (Operation op : operations) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("wsdlURL", wsdlUrl);
            parameters.put("serviceName", port.service().toString());
            parameters.put("portName", port.qname().toString());
            parameters.put("dataFormat", "PAYLOAD");
            parameters.put("defaultOperationName", op.name());
            parameters.put("defaultOperationNamespace", op.namespace());

            List<Object> steps = new ArrayList<>();
            Map<String, Object> remove = new LinkedHashMap<>();
            remove.put("note", "the caller must not choose the operation or the address of the call");
            remove.put("pattern", PRODUCER_REMOVE_HEADERS);
            steps.add(Map.of("removeHeaders", remove));
            Map<String, Object> to = new LinkedHashMap<>();
            to.put("uri", uri);
            to.put("parameters", parameters);
            steps.add(Map.of("to", to));
            if (op.output() != null) {
                steps.add(Map.of("convertBodyTo", Map.of("type", "java.lang.String")));
            }

            Map<String, Object> from = new LinkedHashMap<>();
            from.put("uri", "direct:" + op.name());
            from.put("steps", steps);

            Map<String, Object> route = new LinkedHashMap<>();
            route.put("id", op.name());
            route.put("description", "Calls " + op.name() + " of " + port.service().getLocalPart() + ": the body is the "
                                     + op.input().getLocalPart() + " request element"
                                     + (op.output() != null
                                             ? ", and the reply the " + op.output().getLocalPart() + " response element"
                                             : " (one-way)"));
            route.put("from", from);
            routes.add(Map.of("route", route));
        }

        StringBuilder sb = header("producer");
        sb.append("#\n");
        sb.append("# Send the request element (XML) to direct:<operation>, such as direct:")
                .append(operations.get(0).name()).append(".\n");
        return sb + dump(routes);
    }

    private StringBuilder header(String kind) {
        StringBuilder sb = new StringBuilder();
        // only NCNames from the WSDL go into the comments
        sb.append("# A cxf: ").append(kind).append(" in PAYLOAD mode for the service ").append(port.service().getLocalPart())
                .append(", port ").append(port.name()).append(" (SOAP ").append(port.soapVersion()).append(")\n");
        sb.append("# Scaffolded by: camel generate wsdl --route=").append(kind).append('\n');
        return sb;
    }

    /**
     * Checks that the port can be scaffolded and that its values can go into the YAML.
     */
    private void check() throws WsdlException {
        if (!port.isDocument()) {
            throw new WsdlException(
                    "The port " + port.name()
                                    + " uses the rpc style, which is listed but not scaffolded (only document style)");
        }
        if (operations.isEmpty()) {
            throw new WsdlException("The port " + port.name() + " has no operations to scaffold");
        }
        checkNamespace(port.service().getNamespaceURI());
        checkNamespace(port.namespace());
        for (Operation op : operations) {
            checkName(op.name());
            checkNamespace(op.namespace());
            if (op.input() == null) {
                throw new WsdlException("The operation " + op.name() + " has no request element");
            }
            checkElement(op.input());
            if (op.output() != null) {
                checkElement(op.output());
            }
        }
        checkName(port.service().getLocalPart());
        checkName(port.name());
    }

    private static void checkName(String name) throws WsdlException {
        if (!WsdlModel.isNcName(name)) {
            throw new WsdlException("The name '" + WsdlModel.printable(name) + "' is not a valid NCName");
        }
    }

    private static void checkElement(QName element) throws WsdlException {
        checkName(element.getLocalPart());
        checkNamespace(element.getNamespaceURI());
    }

    private static void checkNamespace(String namespace) throws WsdlException {
        if (namespace != null && !SAFE_URI.matcher(namespace).matches()) {
            throw new WsdlException(
                    "The namespace '" + WsdlModel.printable(namespace)
                                    + "' has characters that are not scaffolded (such as a brace, quote or space)");
        }
    }

    /**
     * The address of the port: scheme, host, port and path, without the user info (it is not written into the route)
     * and without the query (it would become endpoint parameters).
     */
    private URI address() throws WsdlException {
        String raw = port.address();
        String fallback = "http://localhost:" + DEFAULT_PORT + "/" + port.service().getLocalPart();
        URI uri = null;
        if (raw != null && !raw.isBlank()) {
            try {
                uri = new URI(raw.trim());
            } catch (URISyntaxException e) {
                // not a URI
            }
        }
        String scheme = uri != null && uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        if (uri == null || uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) {
            warnings.add("The port " + port.name() + " has no http(s) address"
                         + (raw != null ? " (" + WsdlModel.printable(WsdlModel.maskedAddress(raw)) + ")" : "")
                         + ": the route uses " + fallback);
            return URI.create(fallback);
        }
        if (uri.getRawUserInfo() != null) {
            warnings.add("The user info of the address of the port " + port.name()
                         + " is left out of the route: configure the credentials of the endpoint instead");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            warnings.add("The query of the address of the port " + port.name() + " is left out of the route");
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty()
                ? "/" + port.service().getLocalPart() : uri.getRawPath();
        String answer = scheme + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "") + path;
        if (!SAFE_URI.matcher(answer).matches()) {
            throw new WsdlException("The address of the port " + port.name() + " has characters that are not scaffolded");
        }
        return URI.create(answer);
    }

    private static String placeholder(QName element) {
        // the namespace is a plain URI (checkNamespace), so it needs no escaping in the attribute
        String ns = element.getNamespaceURI();
        return "<" + element.getLocalPart() + (ns.isEmpty() ? "" : " xmlns=\"" + ns + "\"") + "><!-- TODO --></"
               + element.getLocalPart() + ">";
    }

    private static Map<String, Object> constant(String text) {
        return Map.of("constant", Map.of("expression", text));
    }

    private static Map<String, Object> simple(String text) {
        return Map.of("simple", Map.of("expression", text));
    }

    private static String dump(Object document) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        String yaml = new Yaml(options).dump(document);
        // the writer indents the top-level list too: every line starts with two spaces, which are taken away, so the
        // routes start at the first column as in the Camel examples (the same shift of every line keeps the structure)
        String[] lines = yaml.split("\n", -1);
        boolean indented = Arrays.stream(lines).allMatch(l -> l.isEmpty() || l.startsWith("  "));
        if (!indented) {
            return yaml;
        }
        return Arrays.stream(lines).map(l -> l.isEmpty() ? l : l.substring(2)).collect(Collectors.joining("\n"));
    }
}
