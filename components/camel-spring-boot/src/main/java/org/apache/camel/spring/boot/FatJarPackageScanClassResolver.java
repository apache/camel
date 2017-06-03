/**
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
package org.apache.camel.spring.boot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.util.IOHelper;

/**
 * An implementation of the {@code org.apache.camel.spi.PackageScanClassResolver} that is able to
 * scan spring-boot fat jars to find classes contained also in nested jars.
 */
public class FatJarPackageScanClassResolver extends DefaultPackageScanClassResolver {

    private static final String SPRING_BOOT_CLASSIC_LIB_ROOT = "lib/";
    private static final String SPRING_BOOT_BOOT_INF_LIB_ROOT = "BOOT-INF/lib/";
    private static final String SPRING_BOOT_BOOT_INF_CLASSES_ROOT = "BOOT-INF/classes/";

    /**
     * Loads all the class entries from the main JAR and all nested jars.
     *
     * @param stream  the inputstream of the jar file to be examined for classes
     * @param urlPath the url of the jar file to be examined for classes
     * @return all the .class entries from the main JAR and all nested jars
     */
    @Override
    protected List<String> doLoadJarClassEntries(InputStream stream, String urlPath) {
        return doLoadJarClassEntries(stream, urlPath, true, true);
    }

    protected List<String> doLoadJarClassEntries(InputStream stream, String urlPath, boolean inspectNestedJars, boolean closeStream) {
        List<String> entries = new ArrayList<String>();

        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();

                if (name != null) {
                    name = name.trim();
                    if (!entry.isDirectory() && name.endsWith(".class")) {
                        entries.add(cleanupSpringbootClassName(name));
                    } else if (inspectNestedJars && !entry.isDirectory() && isSpringBootNestedJar(name)) {
                        String nestedUrl = urlPath + "!/" + name;
                        log.trace("Inspecting nested jar: {}", nestedUrl);

                        List<String> nestedEntries = doLoadJarClassEntries(jarStream, nestedUrl, false, false);
                        entries.addAll(nestedEntries);
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn("Cannot search jar file '" + urlPath + " due to an IOException: " + ioe.getMessage() + ". This exception is ignored.", ioe);
        } finally {
            if (closeStream) {
                // stream is left open when scanning nested jars, otherwise the fat jar stream gets closed
                IOHelper.close(jarStream, urlPath, log);
            }
        }

        return entries;
    }

    private boolean isSpringBootNestedJar(String name) {
        // Supporting both versions of the packaging model
        return name.endsWith(".jar") && (name.startsWith(SPRING_BOOT_CLASSIC_LIB_ROOT) || name.startsWith(SPRING_BOOT_BOOT_INF_LIB_ROOT));
    }

    private String cleanupSpringbootClassName(String name) {
        // Classes inside BOOT-INF/classes will be loaded by the new classloader as if they were in the root
        if (name.startsWith(SPRING_BOOT_BOOT_INF_CLASSES_ROOT)) {
            name = name.substring(SPRING_BOOT_BOOT_INF_CLASSES_ROOT.length());
        }
        return name;
    }

}
