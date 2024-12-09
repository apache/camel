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
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.RuntimeProvider;
import org.apache.camel.catalog.VersionManager;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.tooling.maven.MavenArtifact;

public final class CatalogLoader {

    private static final String DEFAULT_CAMEL_CATALOG = "org.apache.camel.catalog.DefaultCamelCatalog";

    private static final String SPRING_BOOT_CATALOG_PROVIDER = "org.apache.camel.springboot.catalog.SpringBootRuntimeProvider";

    private static final String QUARKUS_CATALOG_PROVIDER = "org.apache.camel.catalog.quarkus.QuarkusRuntimeProvider";

    private CatalogLoader() {
    }

    public static CamelCatalog loadCatalog(String repos, String version) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null || version.isEmpty() || version.equals(answer.getCatalogVersion())) {
            answer.enableCache();
            return answer;
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(null);
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.setRepositories(repos);
        try {
            downloader.start();

            // download camel-catalog for that specific version
            MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + version);
            }

            // re-create answer with the classloader to be able to load resources in this catalog
            Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(DEFAULT_CAMEL_CATALOG);
            if (clazz != null) {
                answer.setVersionManager(new DownloadCatalogVersionManager(version, cl));
                RuntimeProvider provider = ObjectHelper.newInstance(clazz);
                if (provider != null) {
                    answer.setRuntimeProvider(provider);
                }
            }
            answer.enableCache();
        } finally {
            downloader.stop();
        }

        return answer;
    }

    public static CamelCatalog loadSpringBootCatalog(String repos, String version) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null) {
            version = answer.getCatalogVersion();
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.setRepositories(repos);
        try {
            downloader.start();

            MavenArtifact ma;
            String camelCatalogVersion = version;
            try {
                ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", camelCatalogVersion);
            } catch (DownloadException ex) {
                // fallback, in case camel spring boot version differ from camel version
                camelCatalogVersion = answer.getCatalogVersion();
                ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", camelCatalogVersion);
            }
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + camelCatalogVersion);
            }

            ma = downloader.downloadArtifact("org.apache.camel.springboot", "camel-catalog-provider-springboot", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException(
                        "Cannot download org.apache.camel.springboot:camel-catalog-provider-springboot:" + version);
            }

            Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(SPRING_BOOT_CATALOG_PROVIDER);
            if (clazz != null) {
                Class<CamelCatalog> clazz2 = (Class<CamelCatalog>) cl.loadClass(DEFAULT_CAMEL_CATALOG);
                if (clazz2 != null) {
                    answer = ObjectHelper.newInstance(clazz2);
                }
                RuntimeProvider provider = ObjectHelper.newInstance(clazz);
                if (provider != null) {
                    answer.setRuntimeProvider(provider);
                }
                // use classloader that loaded spring-boot provider to ensure we can load its resources
                answer.getVersionManager().setClassLoader(cl);
            }
            answer.enableCache();
        } finally {
            downloader.stop();
        }

        return answer;
    }

    public static CamelCatalog loadQuarkusCatalog(String repos, String quarkusVersion, String quarkusGroupId) throws Exception {
        String camelQuarkusVersion = null;
        CamelCatalog answer = new DefaultCamelCatalog();

        if (quarkusVersion == null) {
            return answer;
        }
        // quarkus 3.2.x and older must have .Final
        boolean finalSuffix = VersionHelper.isLE(quarkusVersion, "3.2");
        if (finalSuffix && !quarkusVersion.endsWith(".Final")) {
            quarkusVersion += ".Final";
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setRepositories(repos);
        downloader.setClassLoader(cl);
        try {
            downloader.start();

            // shrinkwrap does not return POM file as result (they are hardcoded to be filtered out)
            // so after this we download a JAR and then use its File location to compute the file for the downloaded POM
            if (quarkusGroupId == null) {
                quarkusGroupId = "io.quarkus.platform";
            }
            MavenArtifact ma = downloader.downloadArtifact(quarkusGroupId, "quarkus-camel-bom:pom", quarkusVersion);
            if (ma != null && ma.getFile() != null) {
                String name = ma.getFile().getAbsolutePath();
                File file = new File(name);
                if (file.exists()) {
                    DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document dom = db.parse(file);

                    // grab what exact camelVersion and camelQuarkusVersion we are using
                    NodeList nl = dom.getElementsByTagName("dependency");
                    for (int i = 0; i < nl.getLength(); i++) {
                        Element node = (Element) nl.item(i);
                        String g = node.getElementsByTagName("groupId").item(0).getTextContent();
                        String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
                        if ("org.apache.camel.quarkus".equals(g) && "camel-quarkus-catalog".equals(a)) {
                            camelQuarkusVersion = node.getElementsByTagName("version").item(0).getTextContent();
                        }
                    }
                }
            }

            if (camelQuarkusVersion != null) {
                // download camel-quarkus-catalog we use to know if we have an extension or not
                downloader.downloadDependency("org.apache.camel.quarkus", "camel-quarkus-catalog", camelQuarkusVersion);

                Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(QUARKUS_CATALOG_PROVIDER);
                if (clazz != null) {
                    Class<CamelCatalog> clazz2 = (Class<CamelCatalog>) cl.loadClass(DEFAULT_CAMEL_CATALOG);
                    if (clazz2 != null) {
                        answer = ObjectHelper.newInstance(clazz2);
                    }
                    RuntimeProvider provider = ObjectHelper.newInstance(clazz);
                    if (provider != null) {
                        answer.setRuntimeProvider(provider);
                    }
                    // use classloader that loaded quarkus provider to ensure we can load its resources
                    answer.getVersionManager().setClassLoader(cl);
                }
            }
            answer.enableCache();
        } finally {
            downloader.stop();
        }

        return answer;
    }

    private static final class DownloadCatalogVersionManager implements VersionManager {

        private ClassLoader classLoader;
        private final String version;

        public DownloadCatalogVersionManager(String version, ClassLoader classLoader) {
            this.version = version;
            this.classLoader = classLoader;
        }

        @Override
        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public String getLoadedVersion() {
            return version;
        }

        @Override
        public boolean loadVersion(String version) {
            return this.version.equals(version);
        }

        @Override
        public String getRuntimeProviderLoadedVersion() {
            return version;
        }

        @Override
        public boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version) {
            return true;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return classLoader.getResourceAsStream(name);
        }
    }

}
