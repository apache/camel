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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.tooling.maven.MavenGav;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads the dependencies of a properties file that changed while the application runs, such as a
 * camel.jbang.dependencies line added to application.properties in dev mode, so the beans and components of the reload
 * can use them. The dependencies of the properties files are otherwise only downloaded when the application starts
 * ({@link CommandLineDependencyDownloader}), while a //DEPS line in a source file is downloaded in a reload as well.
 * <p/>
 * A library that is on the classpath in another version is not downloaded, as both versions would then be on the
 * classpath: changing the version needs a restart.
 */
public final class ReloadDependencyDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(ReloadDependencyDownloader.class);

    private final CamelContext camelContext;
    private final DependencyDownloader downloader;

    public ReloadDependencyDownloader(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    /**
     * Downloads the dependencies in the given keys of the properties (comma separated, as when the application starts)
     * that are not on the classpath.
     *
     * @return the dependencies that are not downloaded as another version of them is on the classpath, which need a
     *         restart
     */
    public List<String> download(Properties properties, String... keys) {
        List<String> restart = new ArrayList<>();
        if (downloader == null) {
            return restart;
        }
        for (String key : keys) {
            String value = properties.getProperty(key);
            if (value == null) {
                continue;
            }
            for (String dep : value.split(",")) {
                dep = dep.trim();
                if (dep.isEmpty()) {
                    continue;
                }
                MavenGav gav = CatalogDependencyResolver.resolve(dep, camelContext.getVersion());
                String groupId = gav.getGroupId();
                String artifactId = gav.getArtifactId();
                String version = gav.getVersion();
                if (artifactId == null || downloader.alreadyOnClasspath(groupId, artifactId, version)) {
                    continue;
                }
                String loaded = loadedVersion(groupId, artifactId);
                if (loaded != null) {
                    LOG.warn("The dependency {}:{}:{} in {} is not downloaded, as version {} is on the classpath:"
                             + " restart to change the version",
                            groupId, artifactId, version, key, loaded);
                    restart.add(groupId + ":" + artifactId + ":" + version);
                    continue;
                }
                LOG.info("Downloading the dependency {}:{}:{} added to {}", groupId, artifactId, version, key);
                downloader.downloadDependency(groupId, artifactId, version);
            }
        }
        return restart;
    }

    /**
     * The version of the library that the downloader has put on the classpath, from the path of its JAR in the Maven
     * repository (such as .../com/github/javafaker/javafaker/1.0.1/javafaker-1.0.1.jar), or null.
     */
    private String loadedVersion(String groupId, String artifactId) {
        if (downloader.getClassLoader() instanceof URLClassLoader ucl) {
            String dir = "/" + groupId.replace('.', '/') + "/" + artifactId + "/";
            for (URL url : ucl.getURLs()) {
                String path = url.toString();
                int pos = path.indexOf(dir);
                if (pos < 0) {
                    continue;
                }
                String rest = path.substring(pos + dir.length());
                int slash = rest.indexOf('/');
                if (slash > 0 && rest.startsWith(artifactId + "-", slash + 1)) {
                    return rest.substring(0, slash);
                }
            }
        }
        return null;
    }
}
