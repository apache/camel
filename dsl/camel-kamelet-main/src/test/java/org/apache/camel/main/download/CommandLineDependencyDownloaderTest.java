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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandLineDependencyDownloaderTest {

    @Test
    void camelShorthandUsesCatalogCoordinatesAndFallsBackForUncatalogedArtifacts() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> downloaded = new ArrayList<>();
        try (SimpleCamelContext context = new SimpleCamelContext() {
            @Override
            public String getVersion() {
                return "0.0.1";
            }
        }) {
            context.addService(recordingDownloader(context, downloaded));

            CommandLineDependencyDownloader command = new CommandLineDependencyDownloader(
                    context,
                    "camel:whatsapp, camel-openapi-validator, camel:uncataloged, org.apache.camel:camel-whatsapp:1.2.3");
            command.init();
        }

        assertEquals(List.of(
                catalog.componentModel("whatsapp").toGav(),
                catalog.otherModel("openapi-validator").toGav(),
                "org.apache.camel:camel-uncataloged:0.0.1",
                "org.apache.camel:camel-whatsapp:1.2.3"), downloaded);
    }

    private static DependencyDownloader recordingDownloader(SimpleCamelContext context, List<String> downloaded) {
        return (DependencyDownloader) Proxy.newProxyInstance(
                CommandLineDependencyDownloaderTest.class.getClassLoader(),
                new Class[] { DependencyDownloader.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        case "toString" -> {
                            return "RecordingDependencyDownloader";
                        }
                        case "downloadDependency" -> {
                            downloaded.add(args[0] + ":" + args[1] + ":" + args[2]);
                            return null;
                        }
                        case "alreadyOnClasspath" -> {
                            return false;
                        }
                        case "getCamelContext" -> {
                            return context;
                        }
                        default -> {
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        }
                    }
                });
    }
}
