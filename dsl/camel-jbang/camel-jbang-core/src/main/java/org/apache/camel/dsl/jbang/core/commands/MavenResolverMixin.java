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

import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import picocli.CommandLine;

/**
 * Options related to Maven artifact resolution.
 */
public class MavenResolverMixin implements MavenResolverMixinSpec {
    @CommandLine.Option(names = {
            "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @CommandLine.Option(names = {
            "--repo",
            "--repos" }, description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    private volatile MavenDownloader downloader;
    private final Object downloaderLock = new Object();

    public static MavenResolverMixin of(Properties props, MavenResolverMixinSpec fallback) {
        MavenResolverMixin result = new MavenResolverMixin();
        result.download
                = Boolean.parseBoolean(props.getProperty(CamelJBangConstants.DOWNLOAD, String.valueOf(fallback.download())));
        result.repos = props.getProperty(CamelJBangConstants.REPOS, fallback.repos());
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
        MavenDownloader d;
        if ((d = downloader) == null) {
            synchronized (downloaderLock) {
                if ((d = downloader) == null) {
                    d = new MavenDownloaderImpl();
                    if (repos != null) {
                        d.setRepos(repos);
                    }
                    d.setOffline(!download);
                    d.build();
                    downloader = d;
                }
            }
        }
        return d;
    }
}
