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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * The services, ports and operations of a WSDL 1.1 document with SOAP 1.1 or SOAP 1.2 bindings, read with the DOM
 * parser of the JDK. Only the WSDL structure is read: the XML schema of the types is not.
 * <p/>
 * The WSDL may come from a remote server, so the parser refuses a DOCTYPE and never loads an external entity. A
 * {@code wsdl:import} of a local WSDL is only followed to a file, and of a remote WSDL only over http or https (not
 * from https down to http) to the host of the WSDL the user gave, so a remote WSDL can neither read a local file nor
 * make the command call another host, such as one of the internal network. A redirect is only followed to the same
 * scheme, host and port, for the same reason. At most {@link #MAX_DOCUMENTS} documents and {@link #MAX_DEPTH} levels
 * are read.
 */
final class WsdlModel {

    static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";
    static final String WSDL2_NS = "http://www.w3.org/ns/wsdl";
    static final String SOAP11_NS = "http://schemas.xmlsoap.org/wsdl/soap/";
    static final String SOAP12_NS = "http://schemas.xmlsoap.org/wsdl/soap12/";

    static final int MAX_DOCUMENTS = 32;
    static final int MAX_DEPTH = 8;
    static final int MAX_BYTES = 16 * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;

    /**
     * An NCName (the type of the name attributes of WSDL 1.1): no colon, no space, no quote, no $ or brace, so it can
     * go into YAML and into a simple expression as it is.
     */
    private static final Pattern NCNAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{M}\\p{N}._\\-]*");

    /**
     * The user info of an address, such as user:password@ in http://user:password@host/path.
     */
    private static final Pattern USER_INFO = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)[^/?#@]*@");

    /**
     * A fault of an operation.
     *
     * @param name    the name of the fault
     * @param element the element of the fault detail, or <tt>null</tt>
     */
    record Fault(String name, QName element) {
    }

    /**
     * An operation of a port.
     *
     * @param name       the name of the operation
     * @param namespace  the namespace of the operation (of its port type), as the defaultOperationNamespace of cxf
     * @param soapAction the SOAP action, or <tt>null</tt>
     * @param style      document or rpc
     * @param use        literal or encoded
     * @param input      the element of the request, or for rpc the wrapper element, or <tt>null</tt>
     * @param output     the element of the response, or <tt>null</tt> for a one-way operation
     * @param oneWay     whether the operation has no response
     * @param faults     the faults
     */
    record Operation(
            String name, String namespace, String soapAction, String style, String use, QName input, QName output,
            boolean oneWay, List<Fault> faults) {
    }

    /**
     * A SOAP port of a service.
     *
     * @param service     the name of the service
     * @param name        the name of the port
     * @param binding     the name of the binding
     * @param soapVersion 1.1 or 1.2
     * @param style       the default style of the binding: document or rpc
     * @param transport   the transport of the binding
     * @param address     the address of the port as the WSDL has it, or <tt>null</tt>
     * @param namespace   the namespace of the port type, which is the namespace of the operations
     * @param operations  the operations
     */
    record Port(
            QName service, String name, QName binding, String soapVersion, String style, String transport, String address,
            String namespace, List<Operation> operations) {

        QName qname() {
            return new QName(service.getNamespaceURI(), name);
        }

        /**
         * Whether the operations are all document style (rpc is listed, but not scaffolded).
         */
        boolean isDocument() {
            return operations.stream().allMatch(o -> "document".equals(o.style()));
        }
    }

    /**
     * A service.
     *
     * @param name  the name
     * @param ports the SOAP ports
     */
    record Service(QName name, List<Port> ports) {
    }

    /**
     * The WSDL cannot be read: it is not well formed, not WSDL 1.1, has a DOCTYPE, refers to something it does not
     * define, or imports a document that cannot be read.
     */
    static final class WsdlException extends Exception {
        WsdlException(String message) {
            super(message);
        }

        WsdlException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Part(String name, QName element, QName type) {
    }

    private record PortTypeOperation(String name, QName input, QName output, List<Fault> faults) {
    }

    private record PortType(QName name, Map<String, PortTypeOperation> operations) {
    }

    private record BindingOperation(String name, String soapAction, String style, String use, List<String> bodyParts,
            String rpcNamespace) {
    }

    private record Binding(
            QName name, QName type, String soapVersion, String style, String transport,
            Map<String, BindingOperation> operations) {
    }

    private record RawPort(String name, QName binding, String address, boolean soapAddress) {
    }

    private record RawService(QName name, List<RawPort> ports) {
    }

    private final String targetNamespace;
    private final List<Service> services;
    private final List<String> warnings;

    private WsdlModel(String targetNamespace, List<Service> services, List<String> warnings) {
        this.targetNamespace = targetNamespace;
        this.services = services;
        this.warnings = warnings;
    }

    /**
     * The target namespace of the WSDL (of the first document).
     */
    String targetNamespace() {
        return targetNamespace;
    }

    /**
     * The services with at least one SOAP port, in the order of the WSDL.
     */
    List<Service> services() {
        return services;
    }

    /**
     * What was left out, such as a port with a binding that is not SOAP.
     */
    List<String> warnings() {
        return warnings;
    }

    /**
     * The location of a WSDL as a URI: a http or https URL, a file: URL, or a file name.
     */
    static URI toUri(String location) {
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) {
            return URI.create(location);
        }
        return Path.of(location).toAbsolutePath().normalize().toUri();
    }

    /**
     * Reads the WSDL and the WSDL documents it imports.
     *
     * @param  location            a http or https URL, a file: URL, or a file name
     * @throws NoSuchFileException when the WSDL is a file that does not exist
     * @throws IOException         when the WSDL cannot be read
     * @throws WsdlException       when the WSDL, or a document it imports, is not a WSDL 1.1 document this can read
     */
    static WsdlModel load(String location) throws IOException, WsdlException {
        URI base;
        try {
            base = toUri(location);
        } catch (IllegalArgumentException e) {
            throw new WsdlException("Not a file or URL: " + location, e);
        }
        String scheme = scheme(base);
        if (!"file".equals(scheme) && !isRemote(scheme)) {
            throw new WsdlException("Not a file or http(s) URL: " + location);
        }
        if (isRemote(scheme) && host(base).isEmpty()) {
            throw new WsdlException("Not a http(s) URL with a host: " + location);
        }
        // one http client for the WSDL and the documents it imports
        try (Reader reader = new Reader(base)) {
            reader.read(base, 0);
            return reader.build();
        }
    }

    /**
     * Reads a document of a remote WSDL that is not a WSDL document, such as a schema that its types import, with the
     * rules of a {@code wsdl:import}: only from the host of the WSDL, not from https down to http, and a redirect only
     * to the same scheme, host and port.
     *
     * @param  root        the WSDL the user gave
     * @param  uri         the document
     * @throws IOException when the document cannot be read, or is refused
     */
    static byte[] readDocument(URI root, String uri) throws IOException {
        URI target;
        try {
            target = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IOException("Not a valid URI: " + printable(uri), e);
        }
        String refused = refusedImport(root, root, target);
        if (refused != null) {
            throw new IOException("The document " + printable(uri) + " is not read: " + refused);
        }
        try (Reader reader = new Reader(root)) {
            return reader.readBytes(target).data();
        } catch (WsdlException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Why the WSDL at {@code base} does not import the document at {@code imported}, or <tt>null</tt> when it does.
     * <p/>
     * A local WSDL only imports files. A remote WSDL only imports over http or https (http to https is fine, https down
     * to http is not), and only from the host of the WSDL the user gave ({@code root}): the WSDL documents of a service
     * are on the same server, while an import of another host would let a remote WSDL make the command call any host
     * the user can reach, such as one of the internal network.
     *
     * @param  root     the WSDL the user gave
     * @param  base     the WSDL with the import
     * @param  imported the location of the import, resolved against the base
     * @return          the reason, or <tt>null</tt> when the import is followed
     */
    static String refusedImport(URI root, URI base, URI imported) {
        String baseScheme = scheme(base);
        String importScheme = scheme(imported);
        if ("file".equals(baseScheme)) {
            return "file".equals(importScheme) ? null : "a WSDL file only imports files, not " + importScheme;
        }
        if (!isRemote(importScheme)) {
            return "a remote WSDL only imports over http(s), not " + importScheme;
        }
        if ("https".equals(baseScheme) && "http".equals(importScheme)) {
            return "a WSDL read over https does not import over http";
        }
        if (!host(root).equals(host(imported))) {
            return "it is on the host " + host(imported) + ", not on the host of the WSDL (" + host(root)
                   + "); save the documents as files to read them";
        }
        return null;
    }

    /**
     * Whether the redirect from {@code from} to {@code to} stays on the same scheme, host and port.
     */
    static boolean isSameOrigin(URI from, URI to) {
        return scheme(from).equals(scheme(to)) && host(from).equals(host(to)) && port(from) == port(to);
    }

    private static boolean isRemote(String scheme) {
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static String scheme(URI uri) {
        return uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    }

    private static String host(URI uri) {
        return uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equals(scheme(uri)) ? 443 : 80;
    }

    /**
     * Whether the name is an NCName, which can go into YAML and into a simple expression as it is.
     */
    static boolean isNcName(String name) {
        return name != null && NCNAME.matcher(name).matches();
    }

    /**
     * A document as it was read: its location (after a redirect) and its bytes.
     */
    private record Fetched(URI uri, byte[] data) {
    }

    private static final class Reader implements AutoCloseable {
        private final URI root;
        private HttpClient client;
        private final Set<URI> visited = new HashSet<>();
        private String targetNamespace;
        private final Map<QName, List<Part>> messages = new LinkedHashMap<>();
        private final Map<QName, PortType> portTypes = new LinkedHashMap<>();
        private final Map<QName, Binding> bindings = new LinkedHashMap<>();
        private final List<RawService> rawServices = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        Reader(URI root) {
            this.root = root;
        }

        @Override
        public void close() {
            if (client != null) {
                client.close();
            }
        }

        void read(URI uri, int depth) throws IOException, WsdlException {
            URI normalized = uri.normalize();
            if (!visited.add(normalized)) {
                // imported twice, or an import cycle
                return;
            }
            if (visited.size() > MAX_DOCUMENTS) {
                throw new WsdlException("The WSDL imports more than " + MAX_DOCUMENTS + " documents");
            }
            Fetched fetched = readBytes(normalized);
            // the imports are relative to where the document is (after a redirect)
            URI location = fetched.uri();
            Document doc = parse(location, fetched.data());
            Element root = doc.getDocumentElement();
            if (WSDL2_NS.equals(root.getNamespaceURI())) {
                throw new WsdlException(
                        "WSDL 2.0 is not supported (only WSDL 1.1 with SOAP 1.1 or SOAP 1.2 bindings): " + location);
            }
            if (!WSDL_NS.equals(root.getNamespaceURI()) || !"definitions".equals(root.getLocalName())) {
                throw new WsdlException("Not a WSDL 1.1 document (the root element is not wsdl:definitions): " + location);
            }
            String tns = root.getAttribute("targetNamespace");
            if (targetNamespace == null) {
                targetNamespace = tns;
            }

            for (Element e : children(root, WSDL_NS)) {
                switch (e.getLocalName()) {
                    case "import" -> readImport(location, e, depth);
                    case "message" -> readMessage(tns, e);
                    case "portType" -> readPortType(tns, e);
                    case "binding" -> readBinding(tns, e);
                    case "service" -> readService(tns, e);
                    default -> {
                        // types and documentation: not needed for the listing
                    }
                }
            }
        }

        private void readImport(URI base, Element e, int depth) throws IOException, WsdlException {
            String location = e.getAttribute("location");
            if (location.isBlank()) {
                return;
            }
            if (depth + 1 > MAX_DEPTH) {
                throw new WsdlException("The imports of the WSDL are nested deeper than " + MAX_DEPTH + " levels");
            }
            URI imported;
            try {
                imported = base.resolve(new URI(location.trim()));
            } catch (URISyntaxException ex) {
                throw new WsdlException("The import location " + location + " of " + base + " is not a valid URI", ex);
            }
            String refused = refusedImport(this.root, base, imported);
            if (refused != null) {
                throw new WsdlException("The import " + location + " of " + base + " is not followed: " + refused);
            }
            read(imported, depth + 1);
        }

        private void readMessage(String tns, Element e) throws WsdlException {
            List<Part> parts = new ArrayList<>();
            for (Element p : children(e, WSDL_NS, "part")) {
                parts.add(new Part(p.getAttribute("name"), qname(p, "element"), qname(p, "type")));
            }
            messages.put(new QName(tns, name(e, "message")), parts);
        }

        private void readPortType(String tns, Element e) throws WsdlException {
            Map<String, PortTypeOperation> operations = new LinkedHashMap<>();
            for (Element op : children(e, WSDL_NS, "operation")) {
                String name = name(op, "operation");
                QName input = null;
                QName output = null;
                boolean inputFirst = false;
                List<Fault> faults = new ArrayList<>();
                for (Element c : children(op, WSDL_NS)) {
                    switch (c.getLocalName()) {
                        case "input" -> {
                            input = qname(c, "message");
                            inputFirst = output == null;
                        }
                        case "output" -> output = qname(c, "message");
                        case "fault" -> faults.add(new Fault(name(c, "fault"), qname(c, "message")));
                        default -> {
                            // documentation
                        }
                    }
                }
                if (input == null || !inputFirst) {
                    warnings.add("The operation " + name + " is left out: notification and solicit-response operations "
                                 + "are not supported");
                    continue;
                }
                // an overloaded operation (same name, other input or output names) is listed once
                operations.putIfAbsent(name, new PortTypeOperation(name, input, output, faults));
            }
            QName qn = new QName(tns, name(e, "portType"));
            portTypes.put(qn, new PortType(qn, operations));
        }

        private void readBinding(String tns, Element e) throws WsdlException {
            QName qn = new QName(tns, name(e, "binding"));
            QName type = qname(e, "type");
            String soapVersion = null;
            String style = "document";
            String transport = null;
            Element soapBinding = first(e, SOAP11_NS, "binding");
            String soapNs = SOAP11_NS;
            if (soapBinding != null) {
                soapVersion = "1.1";
            } else {
                soapBinding = first(e, SOAP12_NS, "binding");
                soapNs = SOAP12_NS;
                if (soapBinding != null) {
                    soapVersion = "1.2";
                }
            }
            if (soapBinding != null) {
                if (!soapBinding.getAttribute("style").isBlank()) {
                    style = soapBinding.getAttribute("style");
                }
                transport = soapBinding.getAttribute("transport");
            }
            Map<String, BindingOperation> operations = new LinkedHashMap<>();
            for (Element op : children(e, WSDL_NS, "operation")) {
                String name = name(op, "operation");
                String soapAction = null;
                String opStyle = style;
                Element soapOp = first(op, soapNs, "operation");
                if (soapOp != null) {
                    if (soapOp.hasAttribute("soapAction")) {
                        soapAction = soapOp.getAttribute("soapAction");
                    }
                    if (!soapOp.getAttribute("style").isBlank()) {
                        opStyle = soapOp.getAttribute("style");
                    }
                }
                String use = "literal";
                List<String> bodyParts = null;
                String rpcNamespace = null;
                Element input = first(op, WSDL_NS, "input");
                Element body = input != null ? first(input, soapNs, "body") : null;
                if (body != null) {
                    if (!body.getAttribute("use").isBlank()) {
                        use = body.getAttribute("use");
                    }
                    if (body.hasAttribute("parts")) {
                        bodyParts = List.of(body.getAttribute("parts").trim().split("\\s+"));
                    }
                    if (!body.getAttribute("namespace").isBlank()) {
                        rpcNamespace = body.getAttribute("namespace");
                    }
                }
                operations.putIfAbsent(name, new BindingOperation(name, soapAction, opStyle, use, bodyParts, rpcNamespace));
            }
            bindings.put(qn, new Binding(qn, type, soapVersion, style, transport, operations));
        }

        private void readService(String tns, Element e) throws WsdlException {
            QName qn = new QName(tns, name(e, "service"));
            List<RawPort> ports = new ArrayList<>();
            for (Element p : children(e, WSDL_NS, "port")) {
                String address = null;
                boolean soap = false;
                for (Element a : children(p, null)) {
                    if ("address".equals(a.getLocalName())) {
                        address = a.getAttribute("location");
                        soap = SOAP11_NS.equals(a.getNamespaceURI()) || SOAP12_NS.equals(a.getNamespaceURI());
                        break;
                    }
                }
                ports.add(new RawPort(name(p, "port"), qname(p, "binding"), address, soap));
            }
            rawServices.add(new RawService(qn, ports));
        }

        WsdlModel build() throws WsdlException {
            List<Service> services = new ArrayList<>();
            for (RawService rs : rawServices) {
                List<Port> ports = new ArrayList<>();
                for (RawPort rp : rs.ports()) {
                    Binding binding = bindings.get(rp.binding());
                    if (binding == null) {
                        throw new WsdlException(
                                "The port " + rp.name() + " of the service " + rs.name().getLocalPart()
                                                + " refers to the binding " + rp.binding()
                                                + ", which the WSDL does not define");
                    }
                    if (binding.soapVersion() == null) {
                        warnings.add("The port " + rp.name() + " of the service " + rs.name().getLocalPart()
                                     + " is left out: its binding " + binding.name().getLocalPart()
                                     + " is not a SOAP 1.1 or SOAP 1.2 binding");
                        continue;
                    }
                    PortType portType = portTypes.get(binding.type());
                    if (portType == null) {
                        throw new WsdlException(
                                "The binding " + binding.name().getLocalPart() + " refers to the port type " + binding.type()
                                                + ", which the WSDL does not define");
                    }
                    ports.add(new Port(
                            rs.name(), rp.name(), binding.name(), binding.soapVersion(), binding.style(),
                            binding.transport(), rp.address(), portType.name().getNamespaceURI(),
                            operations(binding, portType)));
                }
                if (!ports.isEmpty()) {
                    services.add(new Service(rs.name(), ports));
                }
            }
            if (services.isEmpty()) {
                throw new WsdlException("The WSDL has no service with a SOAP 1.1 or SOAP 1.2 port");
            }
            return new WsdlModel(targetNamespace, services, warnings);
        }

        private List<Operation> operations(Binding binding, PortType portType) throws WsdlException {
            List<Operation> answer = new ArrayList<>();
            // in the order of the port type
            for (PortTypeOperation pto : portType.operations().values()) {
                BindingOperation bo = binding.operations().get(pto.name());
                if (bo == null) {
                    // not bound: it cannot be called on this port
                    continue;
                }
                boolean rpc = "rpc".equals(bo.style());
                String namespace = portType.name().getNamespaceURI();
                QName input;
                QName output = null;
                if (rpc) {
                    // the wrapper elements of rpc: the operation name, and the operation name with Response
                    String ns = bo.rpcNamespace() != null ? bo.rpcNamespace() : namespace;
                    input = new QName(ns, pto.name());
                    if (pto.output() != null) {
                        output = new QName(ns, pto.name() + "Response");
                    }
                } else {
                    input = element(pto.input(), bo.bodyParts());
                    if (pto.output() != null) {
                        output = element(pto.output(), null);
                    }
                }
                List<Fault> faults = new ArrayList<>();
                for (Fault f : pto.faults()) {
                    faults.add(new Fault(f.name(), element(f.element(), null)));
                }
                answer.add(new Operation(
                        pto.name(), namespace, bo.soapAction(), bo.style(), bo.use(), input, output, pto.output() == null,
                        faults));
            }
            return answer;
        }

        /**
         * The element of the body of a message (the first part with an element, or with a type).
         */
        private QName element(QName message, List<String> bodyParts) throws WsdlException {
            List<Part> parts = messages.get(message);
            if (parts == null) {
                throw new WsdlException("The message " + message + " is not defined in the WSDL");
            }
            for (Part p : parts) {
                if (bodyParts != null && !bodyParts.contains(p.name())) {
                    // a soap:header part
                    continue;
                }
                if (p.element() != null) {
                    return p.element();
                }
                if (p.type() != null) {
                    return p.type();
                }
            }
            return null;
        }

        private Fetched readBytes(URI uri) throws IOException, WsdlException {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                Path path = Path.of(uri);
                if (!Files.isRegularFile(path)) {
                    throw new NoSuchFileException(path.toString());
                }
                if (Files.size(path) > MAX_BYTES) {
                    throw new WsdlException("The WSDL " + uri + " is larger than " + MAX_BYTES + " bytes");
                }
                return new Fetched(uri, Files.readAllBytes(path));
            }
            URI current = uri;
            for (int redirects = 0;; redirects++) {
                HttpRequest request = HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(30)).GET().build();
                HttpResponse<InputStream> response;
                try {
                    response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading " + current, e);
                }
                try (InputStream is = response.body()) {
                    int status = response.statusCode();
                    if (isRedirect(status)) {
                        current = redirect(current, status, response.headers().firstValue("Location").orElse(null),
                                redirects);
                        continue;
                    }
                    if (status != 200) {
                        throw new IOException("HTTP " + status + " from " + current);
                    }
                    return new Fetched(current, readLimited(current, is));
                }
            }
        }

        /**
         * The http client, which does not follow redirects: {@link #redirect} does, only to the same scheme, host and
         * port.
         */
        private HttpClient httpClient() {
            if (client == null) {
                client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .proxy(ProxySelector.getDefault())
                        .build();
            }
            return client;
        }

        private static boolean isRedirect(int status) {
            return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
        }

        /**
         * Where a redirect goes, when it stays on the same scheme, host and port: a redirect to another host (such as
         * one of the internal network) is not followed.
         */
        private static URI redirect(URI from, int status, String location, int redirects)
                throws IOException, WsdlException {
            if (location == null || location.isBlank()) {
                throw new IOException("HTTP " + status + " from " + from + " without a Location header");
            }
            if (redirects >= MAX_REDIRECTS) {
                throw new WsdlException("The WSDL " + from + " is redirected more than " + MAX_REDIRECTS + " times");
            }
            URI to;
            try {
                to = from.resolve(new URI(location.trim()));
            } catch (URISyntaxException e) {
                throw new WsdlException("The redirect of " + from + " to " + location + " is not a valid URI", e);
            }
            if (!isSameOrigin(from, to)) {
                throw new WsdlException(
                        "The redirect of " + from + " to " + location + " is not followed: it is not the same scheme,"
                                        + " host and port (use that URL if you trust it)");
            }
            return to;
        }

        private static byte[] readLimited(URI uri, InputStream is) throws IOException, WsdlException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
                if (bos.size() > MAX_BYTES) {
                    throw new WsdlException("The WSDL " + uri + " is larger than " + MAX_BYTES + " bytes");
                }
            }
            return bos.toByteArray();
        }

        private static Document parse(URI uri, byte[] data) throws WsdlException {
            try {
                DocumentBuilder db = documentBuilder();
                return db.parse(new ByteArrayInputStream(data), uri.toString());
            } catch (SAXException e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                if (message.contains("DOCTYPE")) {
                    throw new WsdlException("The WSDL " + uri + " has a DOCTYPE, which is not allowed", e);
                }
                String line = e instanceof SAXParseException spe ? " (line " + spe.getLineNumber() + ")" : "";
                throw new WsdlException("The WSDL " + uri + " is not well formed" + line + ": " + message, e);
            } catch (IOException | ParserConfigurationException e) {
                throw new WsdlException("The WSDL " + uri + " cannot be parsed: " + e.getMessage(), e);
            }
        }

        private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            // no external entity, ever (the DOCTYPE is refused already)
            db.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed: " + systemId);
            });
            // the errors become the message of the exception, rather than [Fatal Error] lines on stderr
            db.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    // ignore
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            return db;
        }

        /**
         * The name attribute of a WSDL element, which must be an NCName.
         */
        private static String name(Element e, String kind) throws WsdlException {
            String name = e.getAttribute("name");
            if (!isNcName(name)) {
                throw new WsdlException("The " + kind + " name '" + printable(name) + "' is not a valid NCName");
            }
            return name;
        }

        /**
         * The QName of an attribute (such as tns:getOrder), resolved with the namespaces in scope, or <tt>null</tt>.
         */
        private static QName qname(Element e, String attribute) throws WsdlException {
            if (!e.hasAttribute(attribute)) {
                return null;
            }
            String value = e.getAttribute(attribute).trim();
            int pos = value.indexOf(':');
            String prefix = pos > 0 ? value.substring(0, pos) : null;
            String local = pos > 0 ? value.substring(pos + 1) : value;
            if (!isNcName(local) || prefix != null && !isNcName(prefix)) {
                throw new WsdlException(
                        "The " + attribute + " '" + printable(value) + "' of " + e.getLocalName() + " is not a valid QName");
            }
            String ns = e.lookupNamespaceURI(prefix);
            if (prefix != null && ns == null) {
                throw new WsdlException(
                        "The prefix " + prefix + " of " + attribute + "=\"" + printable(value) + "\" of "
                                        + e.getLocalName() + " is not declared");
            }
            return new QName(ns != null ? ns : "", local);
        }

        private static List<Element> children(Element parent, String ns) {
            return children(parent, ns, null);
        }

        private static List<Element> children(Element parent, String ns, String localName) {
            List<Element> answer = new ArrayList<>();
            NodeList nl = parent.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element child && (ns == null || ns.equals(child.getNamespaceURI()))
                        && (localName == null || localName.equals(child.getLocalName()))) {
                    answer.add(child);
                }
            }
            return answer;
        }

        private static Element first(Element parent, String ns, String localName) {
            List<Element> list = children(parent, ns, localName);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /**
     * The text with the control characters (such as the escape of a terminal color code) replaced by a question mark,
     * so a value from a remote WSDL cannot change the terminal.
     */
    static String printable(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                sb.append('?');
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    /**
     * The address with the user info (user:password@) masked, for the listing.
     */
    static String maskedAddress(String address) {
        if (address == null) {
            return null;
        }
        return USER_INFO.matcher(address).replaceFirst("$1xxxxxx@");
    }
}
