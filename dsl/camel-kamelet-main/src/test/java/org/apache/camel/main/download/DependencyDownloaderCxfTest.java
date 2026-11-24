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
package org.apache.camel.main.download;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyDownloaderCxfTest {

    private static final String MODULE = "org.apache.camel:camel-cxf-platform-http";
    private static final String WS_SECURITY = "org.apache.cxf:cxf-rt-ws-security";

    @Test
    void aRouteWithACxfConsumerDownloadsTheModuleAndSetsTheStarterOfTheHttpServer() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("cxf:/orders?serviceClass=org.example.Orders").to("log:orders");
                    from("cxf:/invoices?serviceClass=org.example.Invoices").to("log:invoices");
                }
            });

            // a dependency of the integration (so camel export has it), downloaded once
            assertEquals(List.of(MODULE), downloader.downloaded);
            assertNotNull(transport.starter);
        }
    }

    @Test
    void cxfProducersOnlyNeedNeitherTheModuleNorTheHttpServer() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("cxf:http://localhost:9000/orders?serviceClass=org.example.Orders");
                    // cxfrs is another component (camel-cxf-rest)
                    from("cxfrs:/customers?resourceClasses=org.example.Customers").to("log:customers");
                }
            });

            assertTrue(downloader.downloaded.isEmpty(), downloader.downloaded.toString());
            assertNull(transport.starter);
        }
    }

    @Test
    void theRoutesThatAreAddedBeforeAreChecked() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("cxf:/orders?serviceClass=org.example.Orders").to("log:orders");
                }
            });
            context.addService(new DependencyDownloaderCxf(context, true));

            assertEquals(List.of(MODULE), downloader.downloaded);
            assertNotNull(transport.starter);
        }
    }

    @Test
    void aRouteFromATemplateWithACxfConsumer() throws Exception {
        // such as the route of a Kamelet, which is created from its template
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("soap").templateParameter("address")
                            .from("cxf:{{address}}?serviceClass=org.example.Orders").to("log:orders");
                }
            });
            assertTrue(downloader.downloaded.isEmpty(), downloader.downloaded.toString());

            context.addRouteFromTemplate("orders", "soap", Map.of("address", "/orders"));

            assertEquals(List.of(MODULE), downloader.downloaded);
            assertNotNull(transport.starter);
        }
    }

    @Test
    void aCxfProducerWithAUsernameTokenDownloadsWsSecurity() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("cxf:http://localhost:9000/orders?serviceClass=org.example.Orders"
                                + "&wsSecurityUsername=alice&wsSecurityPassword=RAW(se&cret)");
                    from("direct:other")
                            .to("cxf:http://localhost:9000/invoices?serviceClass=org.example.Invoices"
                                + "&wsSecurityUsername=bob&wsSecurityPassword=secret");
                }
            });

            // of the CXF version of Camel, once, and no platform-http for producers only
            assertEquals(List.of(WS_SECURITY + ":${cxf-version}"), downloader.downloadedWithVersion);
            assertNull(transport.starter);
        }
    }

    @Test
    void aCxfConsumerWithAUsernameTokenDownloadsWsSecurityToo() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("cxf:/orders?serviceClass=org.example.Orders&wsSecurityCallbackHandler=#myHandler")
                            .to("log:orders");
                }
            });

            assertEquals(List.of(MODULE, WS_SECURITY), downloader.downloaded);
        }
    }

    @Test
    void theWsSecurityOptionsThatDoNotTurnOnAUsernameTokenDownloadNothing() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("cxf:http://localhost:9000/orders?serviceClass=org.example.Orders"
                                + "&wsSecurityUsername=&wsSecurityPasswordType=Digest")
                            // not a cxf endpoint
                            .to("log:foo?wsSecurityUsername=alice");
                }
            });

            assertTrue(downloader.downloaded.isEmpty(), downloader.downloaded.toString());
        }
    }

    @Test
    void aKameletWithAnOptionalUsernameTokenDownloadsWsSecurityOnlyWhenItIsSet() throws Exception {
        // as the soap-action Kamelet, whose uri always has the wsSecurity options, with optional parameters
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("soap-action").templateParameter("address")
                            .templateOptionalParameter("wsSecurityUsername")
                            .templateOptionalParameter("wsSecurityPassword")
                            .from("direct:{{address}}")
                            .to("cxf://{{address}}?serviceClass=org.example.Orders"
                                + "&wsSecurityUsername=RAW({{?wsSecurityUsername}})"
                                + "&wsSecurityPassword=RAW({{?wsSecurityPassword}})");
                }
            });

            context.addRouteFromTemplate("plain", "soap-action", Map.of("address", "http://localhost:9000/orders"));
            assertTrue(downloader.downloaded.isEmpty(), downloader.downloaded.toString());

            context.addRouteFromTemplate("secured", "soap-action",
                    Map.of("address", "http://localhost:9000/invoices", "wsSecurityUsername", "alice",
                            "wsSecurityPassword", "secret"));
            assertEquals(List.of(WS_SECURITY), downloader.downloaded);
            // the local properties of the template are not left behind
            assertNull(context.getPropertiesComponent().getLocalProperties());
        }
    }

    @Test
    void aUsernameTokenFromAPropertyPlaceholder() throws Exception {
        RecordingDownloader downloader = new RecordingDownloader();
        FakeTransport transport = new FakeTransport();
        try (CamelContext context = context(downloader, transport)) {
            Properties props = new Properties();
            props.setProperty("soap.user", "alice");
            context.getPropertiesComponent().setInitialProperties(props);
            context.addService(new DependencyDownloaderCxf(context, true));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:none")
                            .to("cxf:http://localhost:9000/orders?serviceClass=org.example.Orders"
                                + "&wsSecurityUsername={{?soap.none}}");
                    from("direct:start")
                            .to("cxf:http://localhost:9000/orders?serviceClass=org.example.Orders"
                                + "&wsSecurityUsername={{soap.user}}");
                }
            });

            assertEquals(List.of(WS_SECURITY), downloader.downloaded);
        }
    }

    @Test
    void isCxfConsumer() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Properties props = new Properties();
            props.setProperty("soap.uri", "cxf:/orders");
            props.setProperty("timer.uri", "timer:tick");
            context.getPropertiesComponent().setInitialProperties(props);

            assertTrue(DependencyDownloaderCxf.isCxfConsumer(context, "cxf:/orders"));
            assertTrue(DependencyDownloaderCxf.isCxfConsumer(context, "cxf://http://0.0.0.0:9000/orders"));
            assertTrue(DependencyDownloaderCxf.isCxfConsumer(context, "cxf:bean:ordersEndpoint"));
            assertTrue(DependencyDownloaderCxf.isCxfConsumer(context, "{{soap.uri}}"));
            assertFalse(DependencyDownloaderCxf.isCxfConsumer(context, "{{timer.uri}}"));
            assertFalse(DependencyDownloaderCxf.isCxfConsumer(context, "{{not.there}}"));
            assertFalse(DependencyDownloaderCxf.isCxfConsumer(context, "cxfrs:/customers"));
            assertFalse(DependencyDownloaderCxf.isCxfConsumer(context, "direct:start"));
            assertFalse(DependencyDownloaderCxf.isCxfConsumer(context, null));
        }
    }

    private static CamelContext context(RecordingDownloader downloader, FakeTransport transport) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addService(downloader);
        // camel-cxf-platform-http is not on the classpath of the test: its transport is bound already
        context.getRegistry().bind(DependencyDownloaderCxf.TRANSPORT_BEAN_NAME, transport);
        return context;
    }

    /** The setter of CxfPlatformHttpTransport that the downloader calls. */
    public static class FakeTransport {

        private Runnable starter;

        public void setHttpServerStarter(Runnable starter) {
            this.starter = starter;
        }
    }

    /** Records the downloads and downloads nothing. */
    private static class RecordingDownloader extends MavenDependencyDownloader {

        private final List<String> downloaded = new CopyOnWriteArrayList<>();
        private final List<String> downloadedWithVersion = new CopyOnWriteArrayList<>();

        @Override
        protected void doBuild() {
            // noop
        }

        @Override
        protected void doInit() {
            // noop
        }

        @Override
        protected void doStart() {
            // noop
        }

        @Override
        protected void doStop() {
            // noop
        }

        @Override
        public void downloadDependency(String groupId, String artifactId, String version) {
            downloaded.add(groupId + ":" + artifactId);
            downloadedWithVersion.add(groupId + ":" + artifactId + ":" + version);
        }
    }
}
