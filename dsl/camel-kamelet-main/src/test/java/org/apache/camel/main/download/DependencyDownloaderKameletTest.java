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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyDownloaderKameletTest {

    private static final String KAMELET = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: my-soap-action
            spec:
              definition:
                title: My SOAP action
              dependencies:
                - "camel:cxf-soap"
                - "camel:kamelet"
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - log: "${body}"
            """;

    @TempDir
    Path dir;

    @Test
    void theDependenciesOfAKameletAreDownloadedWhenARouteThatUsesItIsAdded() throws Exception {
        // CAMEL-27364: the route of a Kamelet is created after the routes, so a doCatch on a class of a dependency of the
        // Kamelet (org.apache.cxf.binding.soap.SoapFault for camel:cxf-soap) failed with ClassNotFoundException
        Files.writeString(dir.resolve("my-soap-action.kamelet.yaml"), KAMELET);
        RecordingDownloader downloader = new RecordingDownloader();
        try (CamelContext context = new DefaultCamelContext()) {
            context.addService(downloader);
            context.getComponent("kamelet", KameletComponent.class).setLocation("file:" + dir);
            context.addService(new DependencyDownloaderKamelet(context, "4.0.0"));

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("kamelet:my-soap-action?address=http://localhost:8080")
                            .to("kamelet:other-not-there").to("kamelet:{{name}}");
                }
            });

            // the route is added, but not created: the dependencies of its Kamelet are already downloaded
            assertTrue(downloader.downloaded.contains("org.apache.camel:camel-cxf-soap"), downloader.downloaded.toString());
            assertFalse(downloader.downloaded.contains("org.apache.camel:camel-kamelet"), downloader.downloaded.toString());
        }
    }

    @Test
    void theNameOfTheKamelet() {
        assertEquals("my-soap-action", DependencyDownloaderKamelet.kameletName("kamelet:my-soap-action"));
        assertEquals("my-soap-action", DependencyDownloaderKamelet.kameletName("kamelet://my-soap-action?a=b"));
        assertEquals("my-soap-action", DependencyDownloaderKamelet.kameletName("kamelet:my-soap-action/myRoute?a=b"));
        // the template of a Kamelet, and a name only known at runtime
        assertNull(DependencyDownloaderKamelet.kameletName("kamelet:source"));
        assertNull(DependencyDownloaderKamelet.kameletName("kamelet:sink"));
        assertNull(DependencyDownloaderKamelet.kameletName("kamelet:{{name}}"));
        assertNull(DependencyDownloaderKamelet.kameletName("kamelet:${header.name}"));
        assertNull(DependencyDownloaderKamelet.kameletName("direct:start"));
        assertNull(DependencyDownloaderKamelet.kameletName(null));
    }

    /** Records the downloads, with nothing on the classpath, and downloads nothing. */
    private static class RecordingDownloader extends MavenDependencyDownloader {

        private final List<String> downloaded = new CopyOnWriteArrayList<>();

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
        }

        @Override
        public void downloadDependency(String groupId, String artifactId, String version, boolean transitively) {
            downloaded.add(groupId + ":" + artifactId);
        }

        @Override
        public boolean alreadyOnClasspath(String groupId, String artifactId, String version) {
            return false;
        }

        @Override
        public void onLoadingKamelet(String name) {
            // noop
        }
    }
}
