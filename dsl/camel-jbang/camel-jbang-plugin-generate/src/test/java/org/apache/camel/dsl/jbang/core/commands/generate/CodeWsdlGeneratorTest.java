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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RemoveHeadersDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodeWsdlGeneratorTest {

    // relative, as a user gives it: the wsdlURL of the scaffold is the same on every machine
    private static final String DIR = "src/test/resources/wsdl/";
    private static final String ORDERS = DIR + "orders.wsdl";
    private static final String NOTIFY = DIR + "notify12.wsdl";
    private static final String MULTI = DIR + "multi.wsdl";
    private static final String RPC = DIR + "rpc.wsdl";
    private static final Path EXPECTED = Paths.get(DIR, "expected");

    /**
     * Run with -Dcamel.wsdl.updateGolden=true to write the expected files from the output (then review the diff).
     */
    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("camel.wsdl.updateGolden");

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();

    @Test
    void listsTheServicesPortsAndOperations() throws Exception {
        assertThat(run(ORDERS)).as(printer.getOutput()).isZero();

        assertGolden("orders.txt", printer.getStdout());
        assertThat(printer.getStderr()).isEmpty();
    }

    @Test
    void listsAsJsonWithTheCxfOptionsOfEachPort() throws Exception {
        assertThat(run(ORDERS, "--json")).as(printer.getOutput()).isZero();

        JsonObject root = (JsonObject) Jsoner.deserialize(printer.getStdout());
        assertGolden("orders.json", Jsoner.prettyPrint(printer.getStdout(), 2));
        JsonObject port = port(root, 0, 0);
        JsonObject cxf = port.getMap("cxf");
        assertThat(cxf.getString("serviceName")).isEqualTo("{http://example.com/orders}OrderService");
        assertThat(cxf.getString("portName")).isEqualTo("{http://example.com/orders}OrderPort");
        assertThat(cxf.getString("wsdlURL")).isEqualTo("file:" + ORDERS);
        assertThat(cxf.getString("address")).isEqualTo("http://localhost:18081/orders");
        assertThat(cxf.getString("defaultOperationNamespace")).isEqualTo("http://example.com/orders");
    }

    @Test
    void scaffoldsAConsumerRouteThatLoadsWithTheYamlDsl() throws Exception {
        assertThat(run(ORDERS, "--route=consumer")).as(printer.getOutput()).isZero();

        String yaml = printer.getStdout();
        assertGolden("orders-consumer.camel.yaml", yaml);
        assertValid(yaml);

        RouteDefinition route = load(yaml).get(0);
        assertThat(route.getId()).isEqualTo("OrderService");
        assertThat(route.getInput().getEndpointUri())
                .startsWith("cxf://http://0.0.0.0:18081/orders?")
                .contains("wsdlURL=file:" + ORDERS, "dataFormat=PAYLOAD", "schemaValidationEnabled=true",
                        "serviceName={http://example.com/orders}OrderService",
                        "portName={http://example.com/orders}OrderPort");
        ChoiceDefinition choice = (ChoiceDefinition) route.getOutputs().get(1);
        assertThat(choice.getWhenClauses()).extracting(w -> w.getExpression().getExpression())
                .containsExactly("${header.CamelCxfOperationName} == 'getOrder'",
                        "${header.CamelCxfOperationName} == 'placeOrder'");
        WhenDefinition getOrder = choice.getWhenClauses().get(0);
        SetBodyDefinition setBody = (SetBodyDefinition) getOrder.getOutputs().get(1);
        assertThat(setBody.getExpression().getExpression())
                .isEqualTo("<getOrderResponse xmlns=\"http://example.com/orders\"><!-- TODO --></getOrderResponse>");
    }

    @Test
    void scaffoldsAProducerRoutePerOperation() throws Exception {
        assertThat(run(ORDERS, "--route=producer")).as(printer.getOutput()).isZero();

        String yaml = printer.getStdout();
        assertGolden("orders-producer.camel.yaml", yaml);
        assertValid(yaml);

        List<RouteDefinition> routes = load(yaml);
        assertThat(routes).extracting(r -> r.getInput().getEndpointUri())
                .containsExactly("direct:getOrder", "direct:placeOrder");
        RouteDefinition getOrder = routes.get(0);
        // the caller cannot steer the call with CamelDestinationOverrideUrl or the operation headers
        RemoveHeadersDefinition remove = (RemoveHeadersDefinition) getOrder.getOutputs().get(0);
        assertThat(remove.getPattern()).isEqualTo(WsdlRouteScaffold.PRODUCER_REMOVE_HEADERS);
        ToDefinition to = (ToDefinition) getOrder.getOutputs().get(1);
        assertThat(to.getEndpointUri())
                .startsWith("cxf://http://localhost:18081/orders?")
                .contains("defaultOperationName=getOrder", "defaultOperationNamespace=http://example.com/orders",
                        "dataFormat=PAYLOAD");
    }

    @Test
    void theOperationOptionChoosesTheOperations() throws Exception {
        assertThat(run(ORDERS, "--route=producer", "--operation=placeOrder")).as(printer.getOutput()).isZero();

        assertThat(load(printer.getStdout())).extracting(RouteDefinition::getId).containsExactly("placeOrder");
    }

    @ParameterizedTest
    @ValueSource(strings = { "spring-boot", "quarkus" })
    void aSpringBootOrQuarkusConsumerListensOnThePathOfTheAddress(String runtime) throws Exception {
        assertThat(run(ORDERS, "--route=consumer", "--runtime=" + runtime)).as(printer.getOutput()).isZero();

        String yaml = printer.getStdout();
        assertValid(yaml);
        assertThat(load(yaml).get(0).getInput().getEndpointUri()).startsWith("cxf:///orders?");
    }

    @Test
    void writesTheRouteToTheOutputFile() throws Exception {
        Path file = dir.resolve("orders.camel.yaml");

        assertThat(run(ORDERS, "--route=consumer", "--output=" + file)).as(printer.getOutput()).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertValid(Files.readString(file));
    }

    @Test
    void listsSoap12AndOneWayOperationsAndMasksTheUserInfo() throws Exception {
        assertThat(run(NOTIFY)).as(printer.getOutput()).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("SOAP       1.2", "(one-way)", "https://xxxxxx@notify.example.com:8443/ws/notify")
                .doesNotContain("secret", "user:");
    }

    @Test
    void aOneWayOperationReturnsNoResponseAndTheUserInfoIsLeftOut() throws Exception {
        assertThat(run(NOTIFY, "--route=consumer")).as(printer.getOutput()).isZero();

        String yaml = printer.getStdout();
        assertValid(yaml);
        assertThat(yaml).doesNotContain("secret", "user", "tenant");
        assertThat(printer.getStderr()).contains("user info", "query");
        RouteDefinition route = load(yaml).get(0);
        assertThat(route.getInput().getEndpointUri()).startsWith("cxf://http://0.0.0.0:8443/ws/notify?");
        ChoiceDefinition choice = (ChoiceDefinition) route.getOutputs().get(1);
        // notify is one-way: only a log
        assertThat(choice.getWhenClauses().get(0).getOutputs()).hasSize(1);
        assertThat(choice.getWhenClauses().get(1).getOutputs()).hasSize(2);

        StringPrinter producer = new StringPrinter();
        assertThat(run(producer, NOTIFY, "--route=producer")).as(producer.getOutput()).isZero();
        assertThat(producer.getStdout()).contains("uri: cxf://https://notify.example.com:8443/ws/notify")
                .doesNotContain("secret", "tenant");
        assertValid(producer.getStdout());
    }

    @Test
    void followsTheImportsAndListsEverySoapPort() throws Exception {
        assertThat(run(MULTI, "--json")).as(printer.getOutput()).isZero();

        JsonObject root = (JsonObject) Jsoner.deserialize(printer.getStdout());
        JsonArray services = root.getJsonArray("services");
        assertThat(services).extracting(s -> ((JsonObject) s).getString("name"))
                .containsExactly("StockService", "StockAdminService");
        assertThat(((JsonObject) services.get(0)).getJsonArray("ports"))
                .extracting(p -> ((JsonObject) p).getString("name") + " " + ((JsonObject) p).getString("soapVersion"))
                .containsExactly("StockSoap 1.1", "StockSoap12 1.2");
        JsonObject getQuote = (JsonObject) port(root, 0, 1).getJsonArray("operations").get(0);
        assertThat(getQuote.getString("input")).isEqualTo("{http://example.com/stock}getQuote");
        JsonObject fault = (JsonObject) getQuote.getJsonArray("faults").get(0);
        assertThat(fault.getString("element")).isEqualTo("{http://example.com/errors}unknownSymbol");
        // the HTTP binding is not SOAP
        assertThat(printer.getStderr()).contains("The port StockHttpGet of the service StockService is left out");
    }

    @Test
    void asksForTheServiceAndPortWhenTheWsdlHasMoreThanOne() throws Exception {
        assertThat(run(MULTI, "--route=consumer")).isEqualTo(2);
        assertThat(printer.getStderr()).contains("--service=StockService --port=StockSoap12 (SOAP 1.2)");

        StringPrinter chosen = new StringPrinter();
        assertThat(run(chosen, MULTI, "--route=consumer", "--service=StockService", "--port=StockSoap12"))
                .as(chosen.getOutput()).isZero();
        assertValid(chosen.getStdout());
        assertThat(load(chosen.getStdout()).get(0).getInput().getEndpointUri())
                .startsWith("cxf://http://0.0.0.0:9001/stock12?")
                .contains("portName={http://example.com/stock/service}StockSoap12");
    }

    @Test
    void anUnknownServicePortOrOperationIsNotFound() throws Exception {
        assertThat(run(ORDERS, "--service=Nope")).isEqualTo(3);
        assertThat(printer.getStderr()).contains("Unknown service: Nope", "OrderService");

        StringPrinter port = new StringPrinter();
        assertThat(run(port, ORDERS, "--port=Nope")).isEqualTo(3);
        assertThat(port.getStderr()).contains("Unknown port: Nope", "OrderPort");

        StringPrinter op = new StringPrinter();
        assertThat(run(op, ORDERS, "--route=producer", "--operation=getOrder,deleteOrder")).isEqualTo(3);
        assertThat(op.getStderr()).contains("Unknown operation: deleteOrder", "getOrder, placeOrder");
    }

    @Test
    void listsButDoesNotScaffoldRpc() throws Exception {
        assertThat(run(RPC)).as(printer.getOutput()).isZero();
        assertThat(printer.getStdout()).contains("STYLE      rpc/literal", "{http://example.com/calc/rpc}add");

        StringPrinter scaffold = new StringPrinter();
        assertThat(run(scaffold, RPC, "--route=consumer")).isEqualTo(1);
        assertThat(scaffold.getStderr()).contains("rpc style");
    }

    @Test
    void refusesWsdl20() throws Exception {
        assertThat(run(DIR + "wsdl20.wsdl")).isEqualTo(1);
        assertThat(printer.getStderr()).contains("WSDL 2.0 is not supported");
    }

    @Test
    void refusesADoctype() throws Exception {
        assertThat(run(DIR + "doctype.wsdl")).isEqualTo(1);
        assertThat(printer.getStderr()).contains("has a DOCTYPE, which is not allowed");
        assertThat(printer.getOutput()).doesNotContain("root:");
    }

    @Test
    void aFileThatDoesNotExist() throws Exception {
        assertThat(run(DIR + "nope.wsdl")).isEqualTo(66);
        assertThat(printer.getStderr()).contains("does not exist");

        StringPrinter json = new StringPrinter();
        assertThat(run(json, DIR + "nope.wsdl", "--json")).isEqualTo(66);
        JsonObject error = (JsonObject) Jsoner.deserialize(json.getStdout());
        assertThat(error.getInteger("code")).isEqualTo(66);
    }

    @Test
    void doesNotFollowAnImportWithAnotherScheme() throws Exception {
        // a WSDL of a remote server must not import a local file, and the other way round
        Path wsdl = Files.writeString(dir.resolve("import.wsdl"), """
                <wsdl:definitions targetNamespace="urn:x" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
                  <wsdl:import namespace="urn:y" location="http://localhost:1/other.wsdl"/>
                </wsdl:definitions>
                """);

        assertThat(run(wsdl.toString())).isEqualTo(1);
        assertThat(printer.getStderr()).contains("is not followed: a WSDL file only imports files, not http");
    }

    @Test
    void anImportCycleIsReadOnce() throws Exception {
        Path wsdl = Files.writeString(dir.resolve("self.wsdl"),
                ordersWith("<wsdl:types>", "<wsdl:import namespace=\"urn:self\" location=\"self.wsdl\"/><wsdl:types>"));

        assertThat(run(wsdl.toString())).as(printer.getOutput()).isZero();
        assertThat(printer.getStdout()).contains("getOrder");
    }

    @Test
    void refusesANameThatIsNotAnNcName() throws Exception {
        Path wsdl = Files.writeString(dir.resolve("evil.wsdl"),
                ordersWith("<wsdl:operation name=\"getOrder\">", "<wsdl:operation name=\"getOrder' || true || '\">"));

        assertThat(run(wsdl.toString(), "--route=consumer")).isEqualTo(1);
        assertThat(printer.getStderr()).contains("is not a valid NCName");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void doesNotScaffoldANamespaceWithAPropertyPlaceholder() throws Exception {
        Path wsdl = Files.writeString(dir.resolve("placeholder.wsdl"),
                ordersWith("http://example.com/orders", "urn:{{env:HOME}}"));

        // listed, as the JSON and the table do not evaluate it
        assertThat(run(wsdl.toString(), "--json")).as(printer.getOutput()).isZero();

        StringPrinter scaffold = new StringPrinter();
        assertThat(run(scaffold, wsdl.toString(), "--route=consumer")).isEqualTo(1);
        assertThat(scaffold.getStderr()).contains("has characters that are not scaffolded");
        assertThat(scaffold.getStdout()).isEmpty();
    }

    @Test
    void readsARemoteWsdlButNotAFileItImports() throws Exception {
        byte[] orders = Files.readAllBytes(Paths.get(ORDERS));
        byte[] evil = """
                <wsdl:definitions targetNamespace="urn:x" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
                  <wsdl:import namespace="urn:y" location="file:///etc/hosts"/>
                </wsdl:definitions>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/orders", exchange -> reply(exchange, orders));
        server.createContext("/evil", exchange -> reply(exchange, evil));
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();

            assertThat(run(base + "/orders?wsdl", "--route=producer")).as(printer.getOutput()).isZero();
            assertValid(printer.getStdout());
            assertThat(printer.getStdout()).contains("wsdlURL: " + base + "/orders?wsdl");

            StringPrinter refused = new StringPrinter();
            assertThat(run(refused, base + "/evil")).isEqualTo(1);
            assertThat(refused.getStderr()).contains("is not followed: a remote WSDL only imports over http(s), not file");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void followsARedirectOnlyToTheSameSchemeHostAndPort() throws Exception {
        byte[] orders = Files.readAllBytes(Paths.get(ORDERS));
        AtomicInteger otherCalls = new AtomicInteger();
        HttpServer other = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        other.createContext("/", exchange -> {
            otherCalls.incrementAndGet();
            reply(exchange, orders);
        });
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/orders", exchange -> reply(exchange, orders));
        server.createContext("/moved", exchange -> redirect(exchange, "/orders?wsdl"));
        server.createContext("/otherPort",
                exchange -> redirect(exchange, "http://localhost:" + other.getAddress().getPort() + "/orders?wsdl"));
        server.createContext("/otherHost",
                exchange -> redirect(exchange, "http://127.0.0.1:" + server.getAddress().getPort() + "/orders?wsdl"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        other.start();
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();

            // the same scheme, host and port
            assertThat(run(base + "/moved")).as(printer.getOutput()).isZero();
            assertThat(printer.getStdout()).contains("getOrder");

            for (String path : List.of("/otherPort", "/otherHost")) {
                StringPrinter refused = new StringPrinter();
                assertThat(run(refused, base + path)).isEqualTo(1);
                assertThat(refused.getStderr()).contains("/orders?wsdl is not followed: it is not the same scheme, host"
                                                         + " and port (use that URL if you trust it)");
            }
            assertThat(otherCalls).hasValue(0);

            StringPrinter loop = new StringPrinter();
            assertThat(run(loop, base + "/loop")).isEqualTo(1);
            assertThat(loop.getStderr()).contains("is redirected more than " + WsdlModel.MAX_REDIRECTS + " times");
        } finally {
            server.stop(0);
            other.stop(0);
        }
    }

    @Test
    void doesNotFollowAnImportOfAnotherHost() throws Exception {
        AtomicInteger otherCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = server.getAddress().getPort();
        byte[] evil = ("""
                <wsdl:definitions targetNamespace="urn:x" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
                  <wsdl:import namespace="urn:y" location="http://127.0.0.1:%d/internal"/>
                </wsdl:definitions>
                """.formatted(port)).getBytes(StandardCharsets.UTF_8);
        server.createContext("/evil", exchange -> reply(exchange, evil));
        server.createContext("/internal", exchange -> {
            otherCalls.incrementAndGet();
            reply(exchange, evil);
        });
        server.start();
        try {
            assertThat(run("http://localhost:" + port + "/evil")).isEqualTo(1);
            assertThat(printer.getStderr()).contains(
                    "is not followed: it is on the host 127.0.0.1, not on the host of the WSDL (localhost)");
            assertThat(otherCalls).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aRemoteWsdlImportsFromItsHostOverHttpOrHttps() throws Exception {
        URI http = URI.create("http://example.com/svc?wsdl");
        URI https = URI.create("https://example.com/svc?wsdl");
        URI file = URI.create("file:///tmp/svc.wsdl");

        // the same host, also on another port, and from http up to https
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://EXAMPLE.com:8080/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(http, http, URI.create("https://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(https, https, URI.create("https://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(file, file, URI.create("file:///tmp/types.wsdl"))).isNull();

        // not from https down to http, not to another host, and not across files and http(s)
        assertThat(WsdlModel.refusedImport(https, https, URI.create("http://example.com/types.wsdl")))
                .isEqualTo("a WSDL read over https does not import over http");
        assertThat(WsdlModel.refusedImport(http, URI.create("https://example.com/types.wsdl"),
                URI.create("http://example.com/more.wsdl")))
                .isEqualTo("a WSDL read over https does not import over http");
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://169.254.169.254/latest/meta-data")))
                .startsWith("it is on the host 169.254.169.254, not on the host of the WSDL (example.com)");
        assertThat(WsdlModel.refusedImport(http, http, file)).isEqualTo("a remote WSDL only imports over http(s), not file");
        assertThat(WsdlModel.refusedImport(https, https, file))
                .isEqualTo("a remote WSDL only imports over http(s), not file");
        assertThat(WsdlModel.refusedImport(file, file, http)).isEqualTo("a WSDL file only imports files, not http");
        assertThat(WsdlModel.refusedImport(file, file, https)).isEqualTo("a WSDL file only imports files, not https");
    }

    @Test
    void anHttpWsdlFollowsAnHttpsImport() throws Exception {
        // nothing listens on the https port: the import is followed (and fails to connect) rather than refused
        byte[] wsdl = """
                <wsdl:definitions targetNamespace="urn:x" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
                  <wsdl:import namespace="urn:y" location="https://localhost:1/types.wsdl"/>
                </wsdl:definitions>
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/svc", exchange -> reply(exchange, wsdl));
        server.start();
        try {
            assertThat(run("http://localhost:" + server.getAddress().getPort() + "/svc")).isEqualTo(1);
            assertThat(printer.getStderr()).contains("Cannot read the WSDL").doesNotContain("is not followed");
        } finally {
            server.stop(0);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void reply(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Test
    void invalidOptions() throws Exception {
        assertThat(run(ORDERS, "--route=both")).isEqualTo(2);
        assertThat(run(ORDERS, "--route=consumer", "--json")).isEqualTo(2);
        assertThat(run(ORDERS, "--output=x.yaml")).isEqualTo(2);
    }

    private static String ordersWith(String target, String replacement) throws Exception {
        String orders = Files.readString(Paths.get(ORDERS));
        assertThat(orders).contains(target);
        return orders.replace(target, replacement);
    }

    private static JsonObject port(JsonObject root, int service, int port) {
        JsonObject s = (JsonObject) root.getJsonArray("services").get(service);
        return (JsonObject) s.getJsonArray("ports").get(port);
    }

    private int run(String... args) throws Exception {
        return run(printer, args);
    }

    private static int run(StringPrinter printer, String... args) throws Exception {
        CodeWsdlGenerator command = new CamelJBangMain().withPrinter(printer).command(new CodeWsdlGenerator());
        CommandLineTestHelper.parse(command, args);
        return command.doCall();
    }

    private static void assertValid(String yaml) throws Exception {
        for (boolean canonical : new boolean[] { false, true }) {
            YamlValidator validator = new YamlValidator(canonical);
            validator.init();
            assertThat(validator.validate(yaml)).as("canonical=" + canonical + ":\n" + yaml).isEmpty();
        }
    }

    /**
     * The routes of the YAML, as the YAML DSL loads them (without starting them: camel-cxf is not on the classpath).
     */
    private static List<RouteDefinition> load(String yaml) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("wsdl.camel.yaml", yaml));
            return List.copyOf(((ModelCamelContext) context).getRouteDefinitions());
        }
    }

    private static void assertGolden(String name, String actual) throws Exception {
        Path file = EXPECTED.resolve(name);
        String normalized = normalize(actual);
        if (UPDATE_GOLDEN) {
            Files.createDirectories(EXPECTED);
            // keep the license header that the build adds to a YAML file
            String header = Files.exists(file) ? licenseHeader(Files.readString(file)) : "";
            Files.writeString(file, header + normalized, StandardCharsets.UTF_8);
        }
        assertThat(file).as("the expected output " + file + " (run with -Dcamel.wsdl.updateGolden=true)").exists();
        String expected = Files.readString(file).replace("\r\n", "\n");
        assertThat(normalized).isEqualTo(normalize(expected.substring(licenseHeader(expected).length())));
    }

    /**
     * The license header at the start of the text (up to and with the first empty line), or empty.
     */
    private static String licenseHeader(String text) {
        String t = text.replace("\r\n", "\n");
        int end = t.indexOf("\n\n");
        if (t.startsWith("#\n# Licensed to the Apache Software Foundation") && end > 0) {
            return t.substring(0, end + 2);
        }
        return "";
    }

    // without the trailing spaces of the table rows, which an editor may remove
    private static String normalize(String text) {
        return text.replace("\r\n", "\n").lines().map(String::stripTrailing).collect(Collectors.joining("\n")).strip()
               + "\n";
    }
}
