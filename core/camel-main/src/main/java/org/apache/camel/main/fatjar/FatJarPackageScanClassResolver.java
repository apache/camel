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
package org.apache.camel.main.fatjar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.camel.support.scan.DefaultPackageScanClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@code org.apache.camel.spi.PackageScanClassResolver} that is able to scan spring-boot fat
 * jars to find classes contained also in nested jars.
 */
public class FatJarPackageScanClassResolver extends DefaultPackageScanClassResolver {
    private static final Logger LOG = LoggerFactory.getLogger(FatJarPackageScanClassResolver.class);

    private static final String SPRING_BOOT_CLASSIC_LIB_ROOT = "lib/";
    private static final String SPRING_BOOT_BOOT_INF_LIB_ROOT = "BOOT-INF/lib/";
    private static final String SPRING_BOOT_BOOT_INF_CLASSES_ROOT = "BOOT-INF/classes/";
    private static final String SPRING_BOOT_WEB_INF_LIB_ROOT = "WEB-INF/lib/";
    private static final String SPRING_BOOT_WEB_INF_CLASSES_ROOT = "WEB-INF/classes/";

    @Override
    protected List<String> doLoadJarClassEntries(InputStream stream, String urlPath) {
        return doLoadJarClassEntries(stream, urlPath, true, true);
    }

    @Override
    protected String parseUrlPath(URL url) {
        String urlPath = url.getFile();

        urlPath = URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Decoded urlPath: {} with protocol: {}", urlPath, url.getProtocol());
        }

        String nested = "nested:";
        if (urlPath.startsWith(nested)) {
            try {
                urlPath = (new URI(url.getFile())).getPath();
                return StringHelper.before(urlPath, "!", urlPath);
            } catch (URISyntaxException e) {
                // ignore
            }
            if (urlPath.startsWith(nested)) {
                urlPath = urlPath.substring(nested.length());
                return StringHelper.before(urlPath, "!", urlPath);
            }
        }

        return super.parseUrlPath(url);
    }

    protected List<String> doLoadJarClassEntries(
            InputStream stream, String urlPath, boolean inspectNestedJars,
            boolean closeStream) {
        List<String> entries = new ArrayList<>();

        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();

                name = name.trim();
                if (!entry.isDirectory() && name.endsWith(".class")) {
                    entries.add(cleanupSpringBootClassName(name));
                } else if (inspectNestedJars && !entry.isDirectory() && isSpringBootNestedJar(name)) {
                    String nestedUrl = urlPath + "!/" + name;
                    LOG.trace("Inspecting nested jar: {}", nestedUrl);

                    List<String> nestedEntries = doLoadJarClassEntries(jarStream, nestedUrl, false, false);
                    entries.addAll(nestedEntries);
                }
            }
        } catch (IOException ioe) {
            LOG.warn("Cannot search jar file '" + urlPath + " due to an IOException: " + ioe.getMessage()
                     + ". This exception is ignored.",
                    ioe);
        } finally {
            if (closeStream) {
                // stream is left open when scanning nested jars, otherwise the fat jar stream gets closed
                IOHelper.close(jarStream, urlPath, LOG);
            }
        }

        return entries;
    }

    private boolean isSpringBootNestedJar(String name) {
        // Supporting both versions of the packaging model
        return name.endsWith(".jar") && (name.startsWith(SPRING_BOOT_CLASSIC_LIB_ROOT)
                || name.startsWith(SPRING_BOOT_BOOT_INF_LIB_ROOT) || name.startsWith(SPRING_BOOT_WEB_INF_LIB_ROOT));
    }

    private String cleanupSpringBootClassName(String name) {
        // Classes inside BOOT-INF/classes will be loaded by the new classloader as if they were in the root
        if (name.startsWith(SPRING_BOOT_BOOT_INF_CLASSES_ROOT)) {
            name = name.substring(SPRING_BOOT_BOOT_INF_CLASSES_ROOT.length());
        }
        if (name.startsWith(SPRING_BOOT_WEB_INF_CLASSES_ROOT)) {
            name = name.substring(SPRING_BOOT_WEB_INF_CLASSES_ROOT.length());
        }
        return name;
    }

}
