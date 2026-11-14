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
import java.util.List;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
import org.apache.camel.tooling.maven.MavenGav;

public final class CatalogLoader {

    public static final String QUARKUS_GROUP_ID = "io.quarkus.platform";

    private static final String SPRING_BOOT_CATALOG_PROVIDER = "org.apache.camel.springboot.catalog.SpringBootRuntimeProvider";

    private static final String QUARKUS_CATALOG_PROVIDER = "org.apache.camel.catalog.quarkus.QuarkusRuntimeProvider";

    private CatalogLoader() {
    }

    public static CamelCatalog loadCatalog(String repos, String version, boolean download) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null || version.isEmpty() || version.equals(answer.getCatalogVersion())) {
            answer.enableCache();
            return answer;
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(null);
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.setRepositories(repos);
        downloader.setDownload(download);
        try {
            downloader.start();

            // download camel-catalog for that specific version
            MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + version);
            }

            // Keep the running catalog API and use the downloaded artifact only for resources.
            answer.setVersionManager(new DownloadCatalogVersionManager(version, cl));
            answer.enableCache();
        } finally {
            downloader.stop();
            downloader.close();
        }

        return answer;
    }

    public static CamelCatalog loadSpringBootCatalog(String repos, String version, boolean download) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null) {
            version = answer.getCatalogVersion();
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        // Provider classes share the running catalog API; resources must come only from the selected artifacts.
        DependencyDownloaderClassLoader resources = new DependencyDownloaderClassLoader(null);
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.setRepositories(repos);
        downloader.setDownload(download);
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
                resources.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + camelCatalogVersion);
            }

            final String camelVersion = version;
            final String sbVersion = PropertyResolver.fromSystemProperty(
                    CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION, () -> camelVersion);
            ma = downloader.downloadArtifact("org.apache.camel.springboot", "camel-catalog-provider-springboot", sbVersion);
            if (ma != null) {
                cl.addFile(ma.getFile());
                resources.addFile(ma.getFile());
            } else {
                throw new IOException(
                        "Cannot download org.apache.camel.springboot:camel-catalog-provider-springboot:" + sbVersion);
            }

            Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(SPRING_BOOT_CATALOG_PROVIDER);
            if (clazz != null) {
                RuntimeProvider provider = ObjectHelper.newInstance(clazz);
                if (provider != null) {
                    answer.setRuntimeProvider(provider);
                }
                answer.setVersionManager(new DownloadCatalogVersionManager(camelCatalogVersion, sbVersion, resources));
            }
            answer.enableCache();
        } finally {
            downloader.stop();
            downloader.close();
        }

        return answer;
    }

    public static CamelCatalog loadQuarkusCatalog(String repos, String quarkusVersion, String quarkusGroupId, boolean download)
            throws Exception {
        if (quarkusVersion == null) {
            return new DefaultCamelCatalog();
        }
        // quarkus 3.2.x and older must have .Final
        boolean finalSuffix = VersionHelper.isLE(quarkusVersion, "3.2.0");
        if (finalSuffix && !quarkusVersion.endsWith(".Final")) {
            quarkusVersion += ".Final";
        }

        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setRepositories(repos);
        downloader.setClassLoader(cl);
        downloader.setDownload(download);
        try {
            downloader.start();
            if (quarkusGroupId == null) {
                quarkusGroupId = QUARKUS_GROUP_ID;
            }
            MavenGav bom = MavenGav.fromCoordinates(quarkusGroupId, "quarkus-camel-bom", quarkusVersion, "pom", null);
            return loadQuarkusCatalog(bom, gav -> downloader.downloadArtifact(
                    gav.getGroupId(), gav.getArtifactId() + ("pom".equals(gav.getPackaging()) ? ":pom" : ""),
                    gav.getVersion()));
        } finally {
            downloader.stop();
            downloader.close();
        }
    }

    static CamelCatalog loadQuarkusCatalog(MavenGav quarkusCamelBom, Function<MavenGav, MavenArtifact> downloader)
            throws Exception {
        String camelQuarkusVersion = null;
        String camelVersion = null;
        CamelCatalog answer = new DefaultCamelCatalog();

        // shrinkwrap does not return POM file as result (they are hardcoded to be filtered out)
        // so after this we download a JAR and then use its File location to compute the file for the downloaded POM
        MavenArtifact ma = downloader.apply(quarkusCamelBom);
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
                        Node version = node.getElementsByTagName("version").item(0);
                        camelQuarkusVersion = version != null ? version.getTextContent().trim() : null;
                    } else if ("org.apache.camel".equals(g) && "camel-catalog".equals(a)) {
                        Node version = node.getElementsByTagName("version").item(0);
                        camelVersion = version != null ? version.getTextContent().trim() : null;
                    }
                }
            }
        }

        if (camelQuarkusVersion == null || camelQuarkusVersion.isEmpty() || camelVersion == null || camelVersion.isEmpty()) {
            throw new IOException(
                    "Cannot determine Camel and Camel Quarkus catalog versions from " + quarkusCamelBom
                                  + "; expected org.apache.camel:camel-catalog and org.apache.camel.quarkus:camel-quarkus-catalog"
                                  + " dependencies with non-empty versions");
        }

        // download camel-quarkus-catalog we use to know if we have an extension or not
        MavenArtifact artifact = downloader.apply(MavenGav.fromCoordinates("org.apache.camel.quarkus",
                "camel-quarkus-catalog", camelQuarkusVersion, "jar", null));
        if (artifact == null || artifact.getFile() == null) {
            throw new IOException("Cannot download org.apache.camel.quarkus:camel-quarkus-catalog:" + camelQuarkusVersion);
        }
        DependencyDownloaderClassLoader cl
                = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        cl.addFile(artifact.getFile());
        DependencyDownloaderClassLoader resources = new DependencyDownloaderClassLoader(null);
        MavenArtifact coreCatalog
                = downloader.apply(MavenGav.fromCoordinates("org.apache.camel", "camel-catalog", camelVersion, "jar", null));
        if (coreCatalog == null || coreCatalog.getFile() == null) {
            throw new IOException("Cannot download org.apache.camel:camel-catalog:" + camelVersion);
        }
        resources.addFile(coreCatalog.getFile());
        resources.addFile(artifact.getFile());
        Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(QUARKUS_CATALOG_PROVIDER);
        RuntimeProvider provider = ObjectHelper.newInstance(clazz);
        answer.setRuntimeProvider(provider);
        answer.setVersionManager(new DownloadCatalogVersionManager(camelVersion, camelQuarkusVersion, resources));
        answer.enableCache();

        return answer;
    }

    public static String resolveCamelVersionFromSpringBoot(String repos, String camelSpringBootVersion, boolean download)
            throws Exception {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setRepositories(repos);
        downloader.setClassLoader(cl);
        downloader.setDownload(download);
        try {
            downloader.start();

            List<MavenArtifact> artifacts
                    = downloader.downloadArtifacts("org.apache.camel.springboot", "camel-catalog-provider-springboot",
                            camelSpringBootVersion, true);
            for (MavenArtifact ma : artifacts) {
                String g = ma.getGav().getGroupId();
                String a = ma.getGav().getArtifactId();
                if ("org.apache.camel".equals(g) && "camel-catalog".equals(a)) {
                    return ma.getGav().getVersion();
                }
            }
        } finally {
            downloader.stop();
            downloader.close();
        }

        return null;
    }

    public static String resolveSpringBootVersionFromCamelSpringBoot(
            String repos, String camelSpringBootVersion, boolean download)
            throws Exception {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setRepositories(repos);
        downloader.setClassLoader(cl);
        downloader.setDownload(download);
        try {
            downloader.start();

            List<MavenArtifact> artifacts
                    = downloader.downloadArtifacts("org.apache.camel.springboot", "camel-core-starter", camelSpringBootVersion,
                            true);
            for (MavenArtifact ma : artifacts) {
                String g = ma.getGav().getGroupId();
                String a = ma.getGav().getArtifactId();
                if ("org.springframework.boot".equals(g) && "spring-boot-starter".equals(a)) {
                    return ma.getGav().getVersion();
                }
            }
        } finally {
            downloader.stop();
            downloader.close();
        }

        return null;
    }

    public static String resolveCamelVersionFromQuarkus(String repos, String camelQuarkusVersion, boolean download)
            throws Exception {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(CatalogLoader.class.getClassLoader());
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setRepositories(repos);
        downloader.setClassLoader(cl);
        downloader.setDownload(download);
        try {
            downloader.start();

            List<MavenArtifact> artifacts = downloader.downloadArtifacts("org.apache.camel.quarkus", "camel-quarkus-catalog",
                    camelQuarkusVersion, true);
            for (MavenArtifact ma : artifacts) {
                String g = ma.getGav().getGroupId();
                String a = ma.getGav().getArtifactId();
                if ("org.apache.camel".equals(g) && "camel-catalog".equals(a)) {
                    return ma.getGav().getVersion();
                }
            }
        } finally {
            downloader.stop();
            downloader.close();
        }

        return null;
    }

    private static final class DownloadCatalogVersionManager implements VersionManager {

        private ClassLoader classLoader;
        private final String version;
        private final String providerVersion;

        public DownloadCatalogVersionManager(String version, ClassLoader classLoader) {
            this(version, version, classLoader);
        }

        public DownloadCatalogVersionManager(String version, String providerVersion, ClassLoader classLoader) {
            this.version = version;
            this.providerVersion = providerVersion;
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
            return providerVersion;
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
