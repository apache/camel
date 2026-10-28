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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyDownloaderComponentResolverTest {

    @Test
    void theApiDescriptionOfACodeFirstRestDslDownloadsOpenApiJava() throws Exception {
        // CAMEL-25768: the rest-api component serves the API description of a code-first REST DSL (apiContextPath),
        // which needs camel-openapi-java, as the contract-first rest-openapi does
        List<String> downloaded = resolve("rest-api");
        assertTrue(downloaded.contains("camel-openapi-java"), downloaded.toString());
        // the http producer is for the contract-first rest-openapi only
        assertFalse(downloaded.contains("camel-http"), downloaded.toString());
    }

    @Test
    void theContractFirstRestDslDownloadsOpenApiJavaAndHttp() throws Exception {
        List<String> downloaded = resolve("rest-openapi");
        assertTrue(downloaded.contains("camel-openapi-java"), downloaded.toString());
        assertTrue(downloaded.contains("camel-http"), downloaded.toString());
    }

    /** The artifacts the resolver asks to download for the component, with nothing on the classpath. */
    private static List<String> resolve(String component) throws Exception {
        List<String> downloaded = new ArrayList<>();
        DependencyDownloader downloader = (DependencyDownloader) Proxy.newProxyInstance(
                DependencyDownloaderComponentResolverTest.class.getClassLoader(),
                new Class<?>[] { DependencyDownloader.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "downloadDependency" -> {
                        downloaded.add((String) args[1]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "RecordingDependencyDownloader";
                    default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
        try (CamelContext context = new DefaultCamelContext()) {
            context.addService(downloader);
            DependencyDownloaderComponentResolver resolver
                    = new DependencyDownloaderComponentResolver(context, null, false, false);
            try {
                resolver.resolveComponent(component, context);
            } catch (Exception e) {
                // the component itself need not be on the test classpath: the downloads are asked before
            }
        }
        return downloaded;
    }
}
