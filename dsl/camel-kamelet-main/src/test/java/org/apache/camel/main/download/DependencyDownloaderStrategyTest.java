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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyDownloaderStrategyTest {

    @Test
    void declaredDependencyIsDownloaded() throws Exception {
        List<String> downloaded = new ArrayList<>();
        try (SimpleCamelContext context = new SimpleCamelContext()) {
            context.addService(recordingDownloader(downloaded, null));

            new DependencyDownloaderStrategy(context).onDependency("com.example:declared:1.0");
        }

        assertEquals(List.of("com.example:declared:1.0"), downloaded);
    }

    @Test
    void requiredDeclaredDependencyThatCannotBeDownloadedFails() throws Exception {
        DownloadException failure = new DownloadException("Cannot download dependency: com.example:missing:1.0");
        List<String> downloaded = new ArrayList<>();
        try (SimpleCamelContext context = new SimpleCamelContext()) {
            context.addService(recordingDownloader(downloaded, failure));

            DependencyDownloaderStrategy strategy = new DependencyDownloaderStrategy(context, true);

            assertSame(failure,
                    assertThrows(DownloadException.class, () -> strategy.onDependency("com.example:missing:1.0")));
        }

        assertEquals(List.of("required:com.example:missing:1.0"), downloaded);
    }

    private static DependencyDownloader recordingDownloader(List<String> downloaded, DownloadException requiredFailure) {
        return (DependencyDownloader) Proxy.newProxyInstance(
                DependencyDownloaderStrategyTest.class.getClassLoader(),
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
                        case "downloadDependencies" -> {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        case "downloadRequiredDependency" -> {
                            downloaded.add("required:" + args[0] + ":" + args[1] + ":" + args[2]);
                            if (requiredFailure != null) {
                                throw requiredFailure;
                            }
                            return null;
                        }
                        default -> {
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        }
                    }
                });
    }
}
