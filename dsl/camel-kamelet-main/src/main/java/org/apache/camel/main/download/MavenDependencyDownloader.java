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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenDependencyDownloader extends ServiceSupport implements DependencyDownloader {

    public static final String MAVEN_CENTRAL_REPO = "https://repo1.maven.org/maven2/";
    public static final String APACHE_SNAPSHOT_REPO = "https://repository.apache.org/snapshots";

    private static final Logger LOG = LoggerFactory.getLogger(MavenDependencyDownloader.class);
    private static final String CP = System.getProperty("java.class.path");

    private String[] bootClasspath;
    private DownloadThreadPool threadPool;
    private ClassLoader classLoader;
    private CamelContext camelContext;
    private final Set<DownloadListener> downloadListeners = new LinkedHashSet<>();
    private String repos;
    private boolean fresh;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void addDownloadListener(DownloadListener downloadListener) {
        CamelContextAware.trySetCamelContext(downloadListener, getCamelContext());
        downloadListeners.add(downloadListener);
    }

    @Override
    public String getRepos() {
        return repos;
    }

    @Override
    public void setRepos(String repos) {
        this.repos = repos;
    }

    @Override
    public boolean isFresh() {
        return fresh;
    }

    @Override
    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    @Override
    public void downloadDependency(String groupId, String artifactId, String version) {
        downloadDependency(groupId, artifactId, version, true);
    }

    @Override
    public void downloadHiddenDependency(String groupId, String artifactId, String version) {
        doDownloadDependency(groupId, artifactId, version, true, true);
    }

    @Override
    public void downloadDependency(String groupId, String artifactId, String version, boolean transitively) {
        doDownloadDependency(groupId, artifactId, version, transitively, false);
    }

    protected void doDownloadDependency(
            String groupId, String artifactId, String version, boolean transitively,
            boolean hidden) {

        if (!hidden) {
            // trigger listener
            for (DownloadListener listener : downloadListeners) {
                listener.onDownloadDependency(groupId, artifactId, version);
            }
        }

        // when running jbang directly then the CP has some existing camel components
        // that essentially is not needed to be downloaded, but we need the listener to trigger
        // to capture that the GAV is required for running the application
        if (CP != null) {
            // is it already on classpath
            String target = artifactId;
            if (version != null) {
                target = target + "-" + version;
            }
            if (CP.contains(target)) {
                // already on classpath
                return;
            }
        }

        // we need version to be able to download from maven
        if (version == null) {
            return;
        }

        String gav = groupId + ":" + artifactId + ":" + version;
        threadPool.download(LOG, () -> {
            LOG.debug("Downloading: {}", gav);
            List<String> deps = List.of(gav);
            List<String> mavenRepos = new ArrayList<>();

            // add maven central first
            mavenRepos.add(MAVEN_CENTRAL_REPO);
            // and custom repos
            if (repos != null) {
                mavenRepos.addAll(Arrays.stream(repos.split(",")).collect(Collectors.toList()));
            }
            // include Apache snapshot to make it easy to use upcoming releases
            if ("org.apache.camel".equals(groupId) && version.contains("SNAPSHOT")) {
                mavenRepos.add(APACHE_SNAPSHOT_REPO);
            }

            List<MavenArtifact> artifacts
                    = MavenDependencyResolver.resolveDependenciesViaAether(deps, mavenRepos, false, fresh, transitively);
            LOG.debug("Resolved {} -> [{}]", gav, artifacts);

            for (MavenArtifact a : artifacts) {
                File file = a.getFile();
                // only add to classpath if not already present (do not trigger listener)
                if (!alreadyOnClasspath(a.getGav().getGroupId(), a.getGav().getArtifactId(),
                        a.getGav().getVersion(), false)) {
                    if (classLoader instanceof DependencyDownloaderClassLoader) {
                        DependencyDownloaderClassLoader ddc = (DependencyDownloaderClassLoader) classLoader;
                        ddc.addFile(file);
                    }
                    LOG.trace("Added classpath: {}", a.getGav());
                }
            }

            if (!artifacts.isEmpty()) {
                // trigger listener after downloaded and added to classloader
                for (DownloadListener listener : downloadListeners) {
                    listener.onDownloadedDependency(groupId, artifactId, version);
                }
            }

        }, gav);
    }

    @Override
    public MavenArtifact downloadArtifact(String groupId, String artifactId, String version) {
        String gav = groupId + ":" + artifactId + ":" + version;
        LOG.debug("DownloadingArtifact: {}", gav);
        List<String> deps = List.of(gav);
        List<String> mavenRepos = new ArrayList<>();

        // add maven central first
        mavenRepos.add(MAVEN_CENTRAL_REPO);
        // and custom repos
        if (repos != null) {
            mavenRepos.addAll(Arrays.stream(repos.split(",")).collect(Collectors.toList()));
        }
        // include Apache snapshot to make it easy to use upcoming releases
        if ("org.apache.camel".equals(groupId) && version.contains("SNAPSHOT")) {
            mavenRepos.add(APACHE_SNAPSHOT_REPO);
        }

        List<MavenArtifact> artifacts
                = MavenDependencyResolver.resolveDependenciesViaAether(deps, mavenRepos, false, fresh, false);
        LOG.debug("Resolved {} -> [{}]", gav, artifacts);

        if (artifacts.size() == 1) {
            return artifacts.get(0);
        }

        return null;
    }

    public boolean alreadyOnClasspath(String groupId, String artifactId, String version) {
        return alreadyOnClasspath(groupId, artifactId, version, true);
    }

    private boolean alreadyOnClasspath(String groupId, String artifactId, String version, boolean listener) {
        // if no artifact then regard this as okay
        if (artifactId == null) {
            return true;
        }

        String target = artifactId;
        if (version != null) {
            target = target + "-" + version;
        }

        if (bootClasspath != null) {
            for (String s : bootClasspath) {
                if (s.contains(target)) {
                    if (listener) {
                        for (DownloadListener dl : downloadListeners) {
                            dl.onDownloadDependency(groupId, artifactId, version);
                        }
                    }
                    // already on classpath
                    return true;
                }
            }
        }

        if (classLoader instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader) classLoader;
            for (URL u : ucl.getURLs()) {
                String s = u.toString();
                if (s.contains(target)) {
                    // trigger listener
                    if (listener) {
                        for (DownloadListener dl : downloadListeners) {
                            dl.onDownloadDependency(groupId, artifactId, version);
                        }
                    }
                    // already on classpath
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doBuild() throws Exception {
        if (classLoader == null && camelContext != null) {
            classLoader = camelContext.getApplicationContextClassLoader();
        }
        threadPool = new DownloadThreadPool();
        threadPool.setCamelContext(camelContext);
        ServiceHelper.buildService(threadPool);
    }

    @Override
    protected void doInit() throws Exception {
        RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
        if (mb != null) {
            bootClasspath = mb.getClassPath().split("[:|;]");
        }
        ServiceHelper.initService(threadPool);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownService(threadPool);
    }
}
