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
package org.apache.camel.dsl.jbang.export.common;

import java.util.Properties;
import java.util.function.BiConsumer;

import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;

/**
 * How Maven artifacts are downloaded: whether downloading is allowed, the additional repositories, the extra default
 * repositories, and whether to use fresh (non-cached) resources.
 */
public class MavenResolver implements MavenResolverMixinSpec {

    private final boolean download;
    private final String repos;
    private final boolean fresh;
    private final String extraRepos;

    private volatile MavenDownloader downloader;
    private final Object downloaderLock = new Object();

    public MavenResolver() {
        this(true, null, false);
    }

    public MavenResolver(boolean download, String repos, boolean fresh) {
        this(download, repos, fresh, null);
    }

    /**
     * @param download   whether downloading is allowed
     * @param repos      the additional repositories
     * @param fresh      whether to use fresh (non-cached) resources
     * @param extraRepos the extra default repositories (comma-separated URLs or id=url pairs, empty for none), used for
     *                   the downloads and in the pom.xml of the exported project; null for the ones of the
     *                   camel.extra.repos system property, else the camel.default.extra.repos.default.value system
     *                   property
     */
    public MavenResolver(boolean download, String repos, boolean fresh, String extraRepos) {
        this.download = download;
        this.repos = repos;
        this.fresh = fresh;
        this.extraRepos = extraRepos;
    }

    public static MavenResolver of(Properties props, MavenResolverMixinSpec fallback) {
        boolean download
                = Boolean.parseBoolean(props.getProperty(CamelJBangConstants.DOWNLOAD, String.valueOf(fallback.download())));
        String repos = props.getProperty(CamelJBangConstants.REPOS, fallback.repos());
        boolean fresh = Boolean.parseBoolean(props.getProperty(CamelJBangConstants.FRESH, String.valueOf(fallback.fresh())));
        return new MavenResolver(download, repos, fresh, fallback.extraRepos());
    }

    /** You would typically want to use the properly configured {@link #downloader()} instead of this value */
    @Override
    public String repos() {
        return repos;
    }

    /** You would typically want to use the properly configured {@link #downloader()} instead of this value */
    @Override
    public boolean download() {
        return download;
    }

    @Override
    public boolean fresh() {
        return fresh;
    }

    @Override
    public String extraRepos() {
        return extraRepos;
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
                    d.setExtraRepos(extraRepos);
                    d.setOffline(!download);
                    d.setFresh(fresh);
                    d.build();
                    downloader = d;
                }
            }
        }
        return d;
    }

    /**
     * Put {@link #download}, {@link #repos} and {@link #fresh} to the given {@link Properties}.
     *
     * @param properties the destination
     */
    public void putTo(BiConsumer<String, String> properties) {
        properties.accept(CamelJBangConstants.DOWNLOAD, String.valueOf(download));
        properties.accept(CamelJBangConstants.REPOS, repos);
        properties.accept(CamelJBangConstants.FRESH, String.valueOf(fresh));
    }
}
