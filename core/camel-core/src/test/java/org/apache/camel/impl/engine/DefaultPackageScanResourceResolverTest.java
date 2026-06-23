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
package org.apache.camel.impl.engine;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPackageScanResourceResolverTest {
    @Test
    public void testFileResourcesScan() throws Exception {
        final DefaultCamelContext ctx = new DefaultCamelContext();
        final PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(ctx);

        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/**/*.xml"))
                .hasSize(4)
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-dummy.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/a?/*.xml"))
                .hasSize(2)
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/b?/*.xml"))
                .hasSize(2)
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/c?/*.xml"))
                .isEmpty();
    }

    @Test
    public void testJarResourcesScanWithSubdirectories(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("test-routes.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addJarDirectory(jos, "routes/");
            addJarDirectory(jos, "routes/sub1/");
            addJarDirectory(jos, "routes/sub2/");
            addJarEntry(jos, "routes/sub1/a.txt", "content-a");
            addJarEntry(jos, "routes/sub2/b.txt", "content-b");
            addJarEntry(jos, "routes/c.txt", "content-c");
        }

        URLClassLoader jarClassLoader = new URLClassLoader(new URL[] { jarPath.toUri().toURL() });
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(ctx);
            resolver.addClassLoader(jarClassLoader);

            Collection<Resource> resources = resolver.findResources("classpath:routes/**/*.txt");

            assertThat(resources).hasSize(3);

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    assertThat(is).isNotNull();
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(content).startsWith("content-");
                }
            }
        } finally {
            jarClassLoader.close();
        }
    }

    private static void addJarDirectory(JarOutputStream jos, String name) throws Exception {
        JarEntry entry = new JarEntry(name);
        jos.putNextEntry(entry);
        jos.closeEntry();
    }

    private static void addJarEntry(JarOutputStream jos, String name, String content) throws Exception {
        jos.putNextEntry(new JarEntry(name));
        jos.write(content.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }
}
