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
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;

public final class CatalogLoader {

    private static final String DEFAULT_CAMEL_CATALOG = "org.apache.camel.catalog.DefaultCamelCatalog";

    private static final String SPRING_BOOT_CATALOG_PROVIDER = "org.apache.camel.springboot.catalog.SpringBootRuntimeProvider";

    private static final String QUARKUS_CATALOG_PROVIDER = "org.apache.camel.catalog.quarkus.QuarkusRuntimeProvider";

    private CatalogLoader() {
    }

    public static CamelCatalog loadCatalog(String repos, String version) throws Exception {
        if (version == null) {
            CamelCatalog answer = new DefaultCamelCatalog();
            answer.enableCache();
            return answer;
        }

        // use kamelet-main to dynamic download dependency via maven
        KameletMain main = new KameletMain();
        try {
            main.setRepos(repos);
            // enable stub in silent mode so we do not use real components
            main.setSilent(true);
            main.setStubPattern("*");
            main.start();

            // wrap downloaded catalog files in an isolated classloader
            DependencyDownloaderClassLoader cl
                    = new DependencyDownloaderClassLoader(null);

            // download camel-catalog for that specific version
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);
            MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + version);
            }

            // re-create answer with the classloader to be able to load resources in this catalog
            Class<CamelCatalog> clazz2
                    = main.getCamelContext().getClassResolver().resolveClass(DEFAULT_CAMEL_CATALOG,
                            CamelCatalog.class);
            CamelCatalog answer = main.getCamelContext().getInjector().newInstance(clazz2);
            answer.setVersionManager(new DownloadCatalogVersionManager(version, cl));
            answer.enableCache();
            return answer;
        } finally {
            main.stop();
        }
    }

    public static CamelCatalog loadSpringBootCatalog(String repos, String version) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null) {
            version = answer.getCatalogVersion();
        }

        // use kamelet-main to dynamic download dependency via maven
        KameletMain main = new KameletMain();
        try {
            main.setRepos(repos);
            main.start();

            // wrap downloaded catalog files in an isolated classloader
            DependencyDownloaderClassLoader cl
                    = new DependencyDownloaderClassLoader(main.getCamelContext().getApplicationContextClassLoader());

            // download camel-catalog for that specific version
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);
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

            answer.setVersionManager(new DownloadCatalogVersionManager(version, cl));
            Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(SPRING_BOOT_CATALOG_PROVIDER);
            if (clazz != null) {
                RuntimeProvider provider = main.getCamelContext().getInjector().newInstance(clazz);
                if (provider != null) {
                    answer.setRuntimeProvider(provider);
                }
            }
            answer.enableCache();

        } finally {
            main.stop();
        }

        return answer;
    }

    public static CamelCatalog loadQuarkusCatalog(String repos, String quarkusVersion) {
        String camelQuarkusVersion = null;
        CamelCatalog answer = new DefaultCamelCatalog(true);

        // quarkus version must end with .Final
        if (quarkusVersion == null) {
            return answer;
        }
        if (!quarkusVersion.endsWith(".Final")) {
            quarkusVersion += ".Final";
        }

        // use kamelet-main to dynamic download dependency via maven
        KameletMain main = new KameletMain();
        try {
            main.setRepos(repos);
            main.start();

            // shrinkwrap does not return POM file as result (they are hardcoded to be filtered out)
            // so after this we download a JAR and then use its File location to compute the file for the downloaded POM
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);
            MavenArtifact ma = downloader.downloadArtifact("io.quarkus.platform", "quarkus-camel-bom:pom", quarkusVersion);
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

                Class<RuntimeProvider> clazz = main.getCamelContext().getClassResolver().resolveClass(QUARKUS_CATALOG_PROVIDER,
                        RuntimeProvider.class);
                if (clazz != null) {
                    RuntimeProvider provider = main.getCamelContext().getInjector().newInstance(clazz);
                    if (provider != null) {
                        // re-create answer with the classloader that loaded quarkus to be able to load resources in this catalog
                        Class<CamelCatalog> clazz2
                                = main.getCamelContext().getClassResolver().resolveClass(DEFAULT_CAMEL_CATALOG,
                                        CamelCatalog.class);
                        answer = main.getCamelContext().getInjector().newInstance(clazz2);
                        answer.setRuntimeProvider(provider);
                        // use classloader that loaded quarkus provider to ensure we can load its resources
                        answer.getVersionManager().setClassLoader(main.getCamelContext().getApplicationContextClassLoader());
                        answer.enableCache();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            main.stop();
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
