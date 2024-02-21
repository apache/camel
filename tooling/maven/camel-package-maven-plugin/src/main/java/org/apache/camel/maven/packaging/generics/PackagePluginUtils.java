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
package org.apache.camel.maven.packaging.generics;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.Index;

public final class PackagePluginUtils {

    private PackagePluginUtils() {
    }

    public static Index readJandexIndex(MavenProject project) throws MojoExecutionException {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        final JandexStore.Jandex jandex = JandexStore.read(output);
        if (jandex.getException() != null) {
            throw new MojoExecutionException("IOException: " + jandex.getException(), jandex.getException());
        }

        return jandex.getIndex();
    }

    public static Index readJandexIndexQuietly(MavenProject project) {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        final JandexStore.Jandex jandex = JandexStore.read(output);
        if (jandex.getException() != null) {
            throw new RuntimeException("IOException: " + jandex.getException(), jandex.getException());
        }

        return jandex.getIndex();
    }

    public static Index readJandexIndexIgnoreMissing(MavenProject project, Log log) throws MojoExecutionException {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        final JandexStore.Jandex jandex = JandexStore.read(output);

        if (jandex.getException() != null) {
            if (!jandex.doesNotExist()) {
                throw new MojoExecutionException(
                        "IOException: " + jandex.getException().getMessage(), jandex.getException());
            }

            log.warn("Jandex reading failed: " + jandex.getException().getMessage());
        }

        return jandex.getIndex();
    }

    public static String joinHeaderAndSource(String licenseHeader, String source) {
        StringBuilder sb = new StringBuilder(licenseHeader);

        sb.append("\n");
        sb.append(source);
        return sb.toString();
    }

}
