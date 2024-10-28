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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;

public class SourceHelper {

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "java", "groovy", "js", "xml", "yaml" };

    public static Source resolveSource(String source) {
        List<Source> resolved = resolveSources(Collections.singletonList(source));
        if (resolved.isEmpty()) {
            throw new RuntimeCamelException("Failed to resolve source file: " + source);
        } else {
            return resolved.get(0);
        }
    }

    public static List<Source> resolveSources(List<String> sources) {
        return resolveSources(sources, false);
    }

    public static List<Source> resolveSources(List<String> sources, boolean compression) {
        List<Source> resolved = new ArrayList<>();
        for (String source : sources) {
            SourceScheme sourceScheme = SourceScheme.fromUri(source);
            String fileExtension = FileUtil.onlyExt(source, true);
            String fileName = SourceScheme.onlyName(FileUtil.onlyName(source));
            if (fileExtension != null) {
                fileName = fileName + "." + fileExtension;
            }
            try {
                switch (sourceScheme) {
                    case GIST -> {
                        StringJoiner all = new StringJoiner(",");
                        GistHelper.fetchGistUrls(source, all);

                        try (ResourceResolver resolver = new GistResourceResolver()) {
                            for (String uri : all.toString().split(",")) {
                                resolved.add(new Source(
                                        sourceScheme,
                                        FileUtil.stripPath(uri),
                                        IOHelper.loadText(resolver.resolve(uri).getInputStream()),
                                        FileUtil.onlyExt(uri), compression));
                            }
                        }
                    }
                    case HTTP -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.HttpResolver()) {
                            resolved.add(new Source(
                                    sourceScheme,
                                    fileName,
                                    IOHelper.loadText(resolver.resolve(source).getInputStream()),
                                    fileExtension, compression));
                        }
                    }
                    case HTTPS -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.HttpsResolver()) {
                            resolved.add(new Source(
                                    sourceScheme,
                                    fileName,
                                    IOHelper.loadText(resolver.resolve(source).getInputStream()),
                                    fileExtension, compression));
                        }
                    }
                    case FILE -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.FileResolver()) {
                            resolved.add(new Source(
                                    sourceScheme,
                                    fileName,
                                    IOHelper.loadText(resolver.resolve(source).getInputStream()),
                                    fileExtension, compression));
                        }
                    }
                    case CLASSPATH -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.ClasspathResolver()) {
                            resolver.setCamelContext(new DefaultCamelContext());
                            resolved.add(new Source(
                                    sourceScheme,
                                    fileName,
                                    IOHelper.loadText(resolver.resolve(source).getInputStream()),
                                    fileExtension, compression));
                        }
                    }
                    case GITHUB, RAW_GITHUB -> {
                        StringJoiner all = new StringJoiner(",");
                        GitHubHelper.fetchGithubUrls(source, all);

                        try (ResourceResolver resolver = new GitHubResourceResolver()) {
                            for (String uri : all.toString().split(",")) {
                                resolved.add(new Source(
                                        sourceScheme,
                                        FileUtil.stripPath(uri),
                                        IOHelper.loadText(resolver.resolve(uri).getInputStream()),
                                        FileUtil.onlyExt(uri), compression));
                            }
                        }
                    }
                    case UNKNOWN -> {
                        if (isAcceptedSourceFile(fileExtension)) {
                            File sourceFile = new File(source);
                            if (!sourceFile.exists()) {
                                throw new FileNotFoundException("Source file '%s' does not exist".formatted(source));
                            }

                            if (!sourceFile.isDirectory()) {
                                try (FileInputStream fis = new FileInputStream(sourceFile)) {
                                    resolved.add(
                                            new Source(
                                                    sourceScheme,
                                                    fileName,
                                                    IOHelper.loadText(fis),
                                                    fileExtension, compression));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to resolve sources", e);
            }
        }
        return resolved;
    }

    public static boolean isAcceptedSourceFile(String fileExt) {
        return Arrays.stream(ACCEPTED_FILE_EXT).anyMatch(e -> e.equalsIgnoreCase(fileExt));
    }

}
