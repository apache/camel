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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.Properties;
import java.util.function.BiConsumer;

import org.apache.camel.dsl.jbang.export.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.MavenResolverMixinSpec;
import org.apache.camel.tooling.maven.MavenDownloader;
import picocli.CommandLine;

/**
 * Options related to Maven artifact resolution.
 */
public class MavenResolverMixin implements MavenResolverMixinSpec {
    @CommandLine.Option(names = {
            "--download" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @CommandLine.Option(names = {
            "--repo",
            "--repos" }, description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources",
                        defaultValue = "false")
    boolean fresh;

    private volatile MavenResolver resolver;
    private final Object resolverLock = new Object();

    public static MavenResolverMixin of(Properties props, MavenResolverMixinSpec fallback) {
        MavenResolverMixin result = new MavenResolverMixin();
        result.download
                = Boolean.parseBoolean(props.getProperty(CamelJBangConstants.DOWNLOAD, String.valueOf(fallback.download())));
        result.repos = props.getProperty(CamelJBangConstants.REPOS, fallback.repos());
        result.fresh = Boolean.parseBoolean(props.getProperty(CamelJBangConstants.FRESH, String.valueOf(fallback.fresh())));
        return result;
    }

    /** You would typically want to use the properly configured {@link #downloader()} instead of this value */
    public String repos() {
        return repos;
    }

    /** You would typically want to use the properly configured {@link #downloader()} instead of this value */
    public boolean download() {
        return download;
    }

    /**
     * @return a lazily and thread-safe initialized {@link MavenDownloader} configured with {@link #repos}
     */
    public MavenDownloader downloader() {
        return toResolver().downloader();
    }

    /**
     * @return the {@link MavenResolver} with the options of this mixin (created once, so its downloader is shared)
     */
    public MavenResolver toResolver() {
        MavenResolver r;
        if ((r = resolver) == null) {
            synchronized (resolverLock) {
                if ((r = resolver) == null) {
                    r = new MavenResolver(download, repos, fresh);
                    resolver = r;
                }
            }
        }
        return r;
    }

    public boolean fresh() {
        return fresh;
    }

    /**
     * Put {@link #download}, {@link #repos} and {@link fresh} to the given {@link Properties}.
     *
     * @param properties the destination
     */
    public void putTo(BiConsumer<String, String> properties) {
        properties.accept(CamelJBangConstants.DOWNLOAD, String.valueOf(download));
        properties.accept(CamelJBangConstants.REPOS, repos);
        properties.accept(CamelJBangConstants.FRESH, String.valueOf(fresh));
    }
}
