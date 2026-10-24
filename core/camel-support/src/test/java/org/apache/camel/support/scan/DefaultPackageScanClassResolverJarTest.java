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
package org.apache.camel.support.scan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.support.scan.jarfixture.routes.ScanTarget;
import org.apache.camel.support.scan.jarfixture.routesupport.SiblingPackageClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Package scanning of classes packaged in a JAR must only find the scanned package and its sub packages, not a sibling
 * package whose name merely starts with the scanned package name.
 */
public class DefaultPackageScanClassResolverJarTest {

    private static final String SCAN_PACKAGE = ScanTarget.class.getPackageName();

    @TempDir
    File tempDir;

    @Test
    public void testScanJarDoesNotIncludeSiblingPackage() throws Exception {
        File jar = createJar(ScanTarget.class, SiblingPackageClass.class);

        DefaultPackageScanClassResolver resolver = new DefaultPackageScanClassResolver();
        try (URLClassLoader jarClassLoader = new URLClassLoader(new URL[] { jar.toURI().toURL() })) {
            resolver.addClassLoader(jarClassLoader);
            resolver.start();

            Set<Class<?>> found = resolver.findImplementations(Object.class, SCAN_PACKAGE);

            assertTrue(found.stream().anyMatch(c -> c.getName().equals(ScanTarget.class.getName())),
                    "Class in the scanned package should be found: " + found);
            assertFalse(found.stream().anyMatch(c -> c.getName().equals(SiblingPackageClass.class.getName())),
                    "Class in a sibling package sharing the name prefix should not be found: " + found);
            assertEquals(1, found.size(), "Only the scanned package should match: " + found);
        } finally {
            resolver.stop();
        }
    }

    private File createJar(Class<?>... classes) throws IOException {
        File jar = new File(tempDir, "scan-fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            for (Class<?> clazz : classes) {
                String entryName = clazz.getName().replace('.', '/') + ".class";
                // directory entries are needed for ClassLoader.getResources(packagePath) to find the package
                String dir = entryName.substring(0, entryName.lastIndexOf('/') + 1);
                out.putNextEntry(new JarEntry(dir));
                out.closeEntry();
                out.putNextEntry(new JarEntry(entryName));
                try (InputStream in = clazz.getClassLoader().getResourceAsStream(entryName)) {
                    out.write(in.readAllBytes());
                }
                out.closeEntry();
            }
        }
        return jar;
    }
}
