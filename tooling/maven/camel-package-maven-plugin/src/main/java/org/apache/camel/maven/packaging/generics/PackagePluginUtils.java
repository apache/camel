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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

public final class PackagePluginUtils {

    private PackagePluginUtils() {
    }

    public static Index readJandexIndex(MavenProject project) throws MojoExecutionException {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        Index index;
        try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
        return index;
    }

    public static String joinHeaderAndSource(String licenseHeader, String source) {
        StringBuilder sb = new StringBuilder(licenseHeader);

        sb.append("\n");
        sb.append(source);
        return sb.toString();
    }

}
