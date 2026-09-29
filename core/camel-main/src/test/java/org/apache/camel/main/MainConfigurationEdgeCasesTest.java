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
package org.apache.camel.main;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.impl.engine.DefaultManagementStrategy;
import org.apache.camel.impl.event.DefaultEventFactory;
import org.apache.camel.spi.CamelMetricsService;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainConfigurationEdgeCasesTest {

    @TempDir
    Path dir;

    public static class MyMetricsService extends ServiceSupport implements CamelMetricsService {
        private CamelContext camelContext;
        private boolean enableMessageHistory;
        private String binders;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public boolean isEnableMessageHistory() {
            return enableMessageHistory;
        }

        public void setEnableMessageHistory(boolean enableMessageHistory) {
            this.enableMessageHistory = enableMessageHistory;
        }

        public String getBinders() {
            return binders;
        }

        public void setBinders(String binders) {
            this.binders = binders;
        }
    }

    @Test
    public void testTraceRestsDefault() {
        Main main = new Main();
        main.addProperty("camel.trace.enabled", "true");
        main.start();
        try {
            assertTrue(main.getCamelContext().isBacklogTracingRests());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMetricsFromJavaApi() {
        MyMetricsService service = new MyMetricsService();
        Main main = new Main();
        main.bind("myMetrics", service);
        main.configure().metrics().withEnabled(true).withEnableMessageHistory(true).withBinders("jvm-info");
        main.start();
        try {
            assertTrue(service.isStarted());
            assertTrue(service.isEnableMessageHistory());
            assertEquals("jvm-info", service.getBinders());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMetricsDisabledFromJavaApi() {
        MyMetricsService service = new MyMetricsService();
        Main main = new Main();
        main.bind("myMetrics", service);
        main.configure().metrics().withEnabled(false).withEnableMessageHistory(true);
        main.start();
        try {
            assertFalse(service.isStarted());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testCloudPropertiesLocation() throws Exception {
        Files.writeString(dir.resolve("greeting"), "fromCloud");

        Main main = new Main();
        main.addInitialProperty("camel.main.cloudPropertiesLocation", dir.toString());
        main.addOverrideProperty("myOverride", "fromOverride");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertEquals("fromCloud", context.resolvePropertyPlaceholders("{{greeting}}"));
            assertEquals("fromOverride", context.resolvePropertyPlaceholders("{{myOverride}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testCloudPropertiesLocationJavaApi() throws Exception {
        Files.writeString(dir.resolve("greeting"), "fromCloud");

        Main main = new Main();
        main.configure().withCloudPropertiesLocation(dir.toString());
        main.start();
        try {
            assertEquals("fromCloud", main.getCamelContext().resolvePropertyPlaceholders("{{greeting}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testStreamCachingOptions() {
        Main main = new Main();
        main.addProperty("camel.main.streamCachingStatisticsEnabled", "true");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertEquals(IOHelper.DEFAULT_BUFFER_SIZE, context.getStreamCachingStrategy().getBufferSize());
            assertTrue(context.getStreamCachingStrategy().getStatistics().isStatisticsEnabled());
        } finally {
            main.stop();
        }
    }

    public static class MyHttpServerFactory implements MainHttpServerFactory {
        private HttpServerConfigurationProperties server;

        @Override
        public Service newHttpServer(CamelContext camelContext, HttpServerConfigurationProperties configuration) {
            this.server = configuration;
            return new ServiceSupport() {
            };
        }

        @Override
        public Service newHttpManagementServer(
                CamelContext camelContext, HttpManagementServerConfigurationProperties configuration) {
            return new ServiceSupport() {
            };
        }
    }

    @Test
    public void testServerExplicitNotUsingGlobalSsl() {
        MyHttpServerFactory factory = new MyHttpServerFactory();
        Main main = new Main();
        main.bind("myFactory", factory);
        main.addProperty("camel.ssl.enabled", "true");
        main.addProperty("camel.ssl.selfSigned", "true");
        main.addProperty("camel.server.enabled", "true");
        main.addProperty("camel.server.useGlobalSslContextParameters", "false");
        main.start();
        try {
            assertFalse(factory.server.isUseGlobalSslContextParameters());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testCustomManagementStrategyWithEventFactory() {
        DefaultManagementStrategy ms = new DefaultManagementStrategy();
        EventFactory ef = new DefaultEventFactory();
        Main main = new Main();
        main.bind("myStrategy", ms);
        main.bind("myEventFactory", ef);
        main.start();
        try {
            assertSame(ms, main.getCamelContext().getManagementStrategy());
            assertSame(ef, main.getCamelContext().getManagementStrategy().getEventFactory());
        } finally {
            main.stop();
        }
    }
}
