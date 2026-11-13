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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JARs on the classpath the JVM was started with, such as the Camel JARs of the Camel CLI.
 * <p>
 * A JVM started with {@code java -jar}, as the Camel launcher is, has only that JAR as its classpath: the other JARs
 * are listed in the {@code Class-Path} of its manifest, so they are read from there.
 */
final class BootClasspath {

    private static final Logger LOG = LoggerFactory.getLogger(BootClasspath.class);

    private final List<String> entries;
    private final Set<String> jarNames = new HashSet<>();

    private BootClasspath(List<String> entries) {
        this.entries = entries;
        for (String entry : entries) {
            String name = FileUtil.stripPath(entry);
            if (name.endsWith(".jar")) {
                jarNames.add(name);
            }
        }
    }

    /**
     * The JARs of the classpath the JVM was started with.
     */
    static BootClasspath of(String classpath) {
        List<String> entries = new ArrayList<>();
        if (classpath != null && !classpath.isBlank()) {
            for (String entry : classpath.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        if (entries.size() == 1 && entries.get(0).endsWith(".jar")) {
            // java -jar: the JARs are in the Class-Path of its manifest
            entries.addAll(manifestClassPath(new File(entries.get(0))));
        }
        return new BootClasspath(entries);
    }

    private static List<String> manifestClassPath(File jar) {
        List<String> answer = new ArrayList<>();
        if (!jar.isFile()) {
            return answer;
        }
        try (JarFile jf = new JarFile(jar)) {
            Manifest manifest = jf.getManifest();
            String classPath = manifest != null ? manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH) : null;
            if (classPath != null) {
                // relative URLs, resolved against the directory of the JAR
                URI base = jar.getAbsoluteFile().getParentFile().toURI();
                for (String url : classPath.trim().split("\\s+")) {
                    if (!url.isEmpty()) {
                        try {
                            answer.add(new File(base.resolve(url)).getPath());
                        } catch (IllegalArgumentException e) {
                            // not a file URL
                            answer.add(url);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("Cannot read the Class-Path of the manifest of: {} due to: {}", jar, e.getMessage(), e);
        }
        return answer;
    }

    /**
     * Whether an entry of the classpath contains the text, such as artifactId-version.
     */
    boolean contains(String text) {
        for (String entry : entries) {
            if (entry.contains(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the JAR of the artifact is on the classpath: the file name is artifactId-version.jar, or
     * artifactId-version-classifier.jar.
     */
    boolean containsJar(String artifactId, String version, String classifier) {
        if (artifactId == null || version == null) {
            return false;
        }
        String name = artifactId + "-" + version;
        if (classifier != null && !classifier.isEmpty()) {
            name += "-" + classifier;
        }
        return jarNames.contains(name + ".jar");
    }

    List<String> getEntries() {
        return entries;
    }
}
