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
package org.apache.camel.dsl.jbang.core.common;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Builds a tiny jar containing the precompiled CachedFakePlugin class so cache-hit tests can verify the fast path loads
 * a class from a URLClassLoader without going through the Maven downloader.
 */
final class FakePluginJar {

    static final String PLUGIN_CLASS = CachedFakePlugin.class.getName();

    private FakePluginJar() {
    }

    static void write(Path target, String pluginName) throws Exception {
        // Copy the existing .class for CachedFakePlugin into a jar; that class implements Plugin already.
        String classResource = PLUGIN_CLASS.replace('.', '/') + ".class";
        byte[] classBytes;
        try (var in = FakePluginJar.class.getClassLoader().getResourceAsStream(classResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test class resource: " + classResource);
            }
            classBytes = in.readAllBytes();
        }
        try (OutputStream fos = Files.newOutputStream(target);
             JarOutputStream jos = new JarOutputStream(fos)) {
            JarEntry classEntry = new JarEntry(classResource);
            jos.putNextEntry(classEntry);
            jos.write(classBytes);
            jos.closeEntry();

            JarEntry svc = new JarEntry(PluginHelper.PLUGIN_SERVICE_DIR + "camel-jbang-plugin-" + pluginName);
            jos.putNextEntry(svc);
            jos.write(("class=" + PLUGIN_CLASS + "\n").getBytes());
            jos.closeEntry();
        }
    }
}
