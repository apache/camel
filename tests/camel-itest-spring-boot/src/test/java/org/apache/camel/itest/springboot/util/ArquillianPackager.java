/**
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
package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.itest.springboot.ITestConfig;
import org.apache.camel.itest.springboot.ITestConfigBuilder;
import org.apache.camel.itest.springboot.arquillian.SpringBootZipExporterImpl;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.ExtensionLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
import org.jboss.shrinkwrap.impl.base.URLPackageScanner;
import org.jboss.shrinkwrap.impl.base.asset.AssetUtil;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencyExclusion;
import org.junit.Assert;

/**
 * Packages a module in a spring-boot compatible nested-jar structure.
 */
public final class ArquillianPackager {

    /**
     * A flag to enable system-out logging.
     * Cannot use logging libraries here.
     */
    private static final boolean DEBUG_ENABLED = false;

    private static final String LIB_FOLDER = "/BOOT-INF/lib";
    private static final String CLASSES_FOLDER = "BOOT-INF/classes";

    private ArquillianPackager() {
    }

    public static Archive<?> springBootPackage(ITestConfig config) throws Exception {

        ExtensionLoader extensionLoader = new ServiceExtensionLoader(Collections.singleton(getExtensionClassloader()));
        extensionLoader.addOverride(ZipExporter.class, SpringBootZipExporterImpl.class);
        ConfigurationBuilder builder = new ConfigurationBuilder().extensionLoader(extensionLoader);
        Configuration conf = builder.build();

        Domain domain = ShrinkWrap.createDomain(conf);

        JavaArchive ark = domain.getArchiveFactory().create(JavaArchive.class, "test.jar");


        ark = ark.addAsManifestResource("BOOT-MANIFEST.MF", "MANIFEST.MF");

        ark = ark.addAsDirectories(LIB_FOLDER);
        if (!CLASSES_FOLDER.equals("")) {
            ark = ark.addAsDirectories(CLASSES_FOLDER);
        }

        if (config.getUseCustomLog()) {
            ark = ark.addAsResource("spring-logback.xml", CLASSES_FOLDER + "/spring-logback.xml");
        }

        for (Map.Entry<String, String> res : config.getResources().entrySet()) {
            ark = ark.addAsResource(res.getKey(), CLASSES_FOLDER + "/" + res.getValue());
        }

        String version = System.getProperty("version_org.apache.camel:camel-core");
        if (version == null) {
            config.getMavenVersion();
        }
        if (version == null) {
            // It is missing when launching from IDE
            List<MavenResolvedArtifact> resolved = Arrays.asList(resolver(config).loadPomFromFile("pom.xml").importRuntimeDependencies().resolve().withoutTransitivity().asResolvedArtifact());
            for (MavenResolvedArtifact dep : resolved) {
                if (dep.getCoordinate().getGroupId().equals("org.apache.camel")) {
                    version = dep.getCoordinate().getVersion();
                    break;
                }
            }
        }

        debug("Resolved version: " + version);
        if (version == null) {
            throw new IllegalStateException("Cannot determine the current version of the camel component");
        }

        List<MavenDependencyExclusion> commonExclusions = new LinkedList<>();
        commonExclusions.add(MavenDependencies.createExclusion("commons-logging", "commons-logging"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-log4j12"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "log4j"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "log4j-slf4j-impl"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j-core"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j-slf4j-impl"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "apache-log4j-extras"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-simple"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-jdk14"));
        commonExclusions.add(MavenDependencies.createExclusion("ch.qos.logback", "logback-classic"));
        commonExclusions.add(MavenDependencies.createExclusion("ch.qos.logback", "logback-core"));

        for (String ex : config.getMavenExclusions()) {
            commonExclusions.add(MavenDependencies.createExclusion(ex));
        }


        // Module dependencies
        List<MavenDependency> additionalDependencies = new LinkedList<>();
        for (String canonicalForm : config.getAdditionalDependencies()) {
            MavenCoordinate coord = MavenCoordinates.createCoordinate(canonicalForm);
            MavenDependency dep = MavenDependencies.createDependency(coord, ScopeType.RUNTIME, false);
            additionalDependencies.add(dep);
        }

//        String mainArtifactId = config.getModuleName() + "-starter";
//        MavenCoordinate mainJar = MavenCoordinates.createCoordinate(config.getMavenGroup(), mainArtifactId, version, PackagingType.JAR, null);
//        // Add exclusions only when not using the starters
//        MavenDependency mainDep = MavenDependencies.createDependency(mainJar, ScopeType.COMPILE, false);
//        moduleDependencies.add(mainDep);


        List<String> testProvidedDependenciesXml = new LinkedList<>();
        List<ScopeType> scopes = new LinkedList<>();
        if (config.getIncludeProvidedDependencies() || config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {

            if (config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {
                testProvidedDependenciesXml.addAll(DependencyResolver.getDependencies(config.getModuleBasePath() + "/pom.xml", ScopeType.TEST.toString()));
                scopes.add(ScopeType.TEST);
            }
            if (config.getIncludeProvidedDependencies()) {
                testProvidedDependenciesXml.addAll(DependencyResolver.getDependencies(config.getModuleBasePath() + "/pom.xml", ScopeType.PROVIDED.toString()));
                scopes.add(ScopeType.PROVIDED);
            }

        }

        List<String> cleanTestProvidedDependenciesXml = new LinkedList<>();
        for (String depXml : testProvidedDependenciesXml) {
            if (validTestDependency(config, depXml, commonExclusions)) {
                depXml = enforceExclusions(config, depXml, commonExclusions);
                //depXml = addBOMVersionWhereMissing(config, depXml);
                cleanTestProvidedDependenciesXml.add(depXml);
            }
        }

        List<String> versionedTestProvidedDependenciesXml = new LinkedList<>();
        if(!cleanTestProvidedDependenciesXml.isEmpty()) {

            File testProvidedResolverPom = createResolverPom(config, cleanTestProvidedDependenciesXml);

            List<MavenResolvedArtifact> artifacts = Arrays.asList(resolver(config)
                    .loadPomFromFile(testProvidedResolverPom)
                    .importDependencies(scopes.toArray(new ScopeType[0]))
                    .resolve()
                    .withoutTransitivity()
                    .asResolvedArtifact());

            Map<String, String> resolvedVersions = new HashMap<>();
            for(MavenResolvedArtifact art : artifacts) {
                String key = art.getCoordinate().getGroupId() + ":" + art.getCoordinate().getArtifactId();
                String val = art.getCoordinate().getVersion();
                resolvedVersions.put(key, val);
            }

            for(String dep : cleanTestProvidedDependenciesXml) {
                dep = setResolvedVersion(config, dep, resolvedVersions);
                versionedTestProvidedDependenciesXml.add(dep);
            }

        }

        File moduleSpringBootPom = createUserPom(config, versionedTestProvidedDependenciesXml);

        List<ScopeType> resolvedScopes = new LinkedList<>();
        resolvedScopes.add(ScopeType.COMPILE);
        resolvedScopes.add(ScopeType.RUNTIME);
        resolvedScopes.addAll(scopes);

        List<File> dependencies = new LinkedList<>();
        dependencies.addAll(Arrays.asList(resolver(config)
                .loadPomFromFile(moduleSpringBootPom)
                .importDependencies(resolvedScopes.toArray(new ScopeType[0]))
                .addDependencies(additionalDependencies)
                .resolve()
                .withTransitivity()
                .asFile()));


        // The spring boot-loader dependency will be added to the main jar, so it should be excluded from the embedded ones
        excludeDependencyRegex(dependencies, "^spring-boot-loader-[0-9].*");

        // Add all dependencies as spring-boot nested jars
        ark = addDependencies(ark, dependencies);

        // Add common packages to main jar
        ark = ark.addPackages(true, "org.jboss.shrinkwrap");

        // Add current classes to both location to be used by different classloaders
        ark = ark.addPackages(true, "org.apache.camel.itest.springboot");
        ark = addSpringbootPackage(ark, "org.apache.camel.itest.springboot");

        // CAMEL-10060 is resolved since 2.18 but some unit tests use custom (non spring-boot enabled) camel contexts
        ark = ark.addPackages(true, "org.apache.camel.converter.myconverter");

        ark = ark.addPackages(true, "org.springframework.boot.loader");

        ClassPath.Builder external = ClassPath.builder().add(ark);

        // overcome limitations of some JDKs
        external.addSystemProperty("javax.xml.accessExternalDTD", "all");
        external.addSystemProperty("javax.xml.accessExternalSchema", "all");

        if (config.getUnitTestEnabled()) {
            external.addSystemProperty("container.user.dir", new File(config.getModuleBasePath()).getCanonicalPath());
            external.addSystemProperty("container.test.resources.dir", new File(config.getModuleBasePath()).getCanonicalPath() + "/target/test-classes");
        }

        // Adding configuration properties
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            if (e.getKey() instanceof String && e.getValue() instanceof String) {
                String key = (String) e.getKey();
                if (key.startsWith(ITestConfigBuilder.CONFIG_PREFIX)) {
                    external.addSystemProperty(key, (String) e.getValue());
                }
            }
        }

        for (Map.Entry<String, String> e : config.getSystemProperties().entrySet()) {
            external.addSystemProperty(e.getKey(), e.getValue());
        }

        return external.build();
    }

    private static File createResolverPom(ITestConfig config, List<String> cleanTestProvidedDependencies) throws Exception {

        String pom;
        try (InputStream pomTemplate = ArquillianPackager.class.getResourceAsStream("/dependency-resolver-pom.xml")) {
            pom = IOUtils.toString(pomTemplate);
        }

        StringBuilder dependencies = new StringBuilder();
        for (String dep : cleanTestProvidedDependencies) {
            dependencies.append(dep);
            dependencies.append("\n");
        }

        pom = pom.replace("<!-- DEPENDENCIES -->", dependencies.toString());

        Map<String, String> resolvedProperties = new TreeMap<>();
        Pattern propPattern = Pattern.compile("(\\$\\{[^}]*\\})");
        Matcher m = propPattern.matcher(pom);
        while (m.find()) {
            String property = m.group();
            String resolved = DependencyResolver.resolveParentProperty(property);
            resolvedProperties.put(property, resolved);
        }

        for (String property : resolvedProperties.keySet()) {
            pom = pom.replace(property, resolvedProperties.get(property));
        }

        File pomFile = new File(config.getModuleBasePath() + "/target/itest-spring-boot-dependency-resolver-pom.xml");
        try (FileWriter fw = new FileWriter(pomFile)) {
            IOUtils.write(pom, fw);
        }

        return pomFile;
    }

    private static File createUserPom(ITestConfig config, List<String> cleanTestProvidedDependencies) throws Exception {

        String pom;
        try (InputStream pomTemplate = ArquillianPackager.class.getResourceAsStream("/application-pom.xml")) {
            pom = IOUtils.toString(pomTemplate);
        }

        StringBuilder dependencies = new StringBuilder();
        for (String dep : cleanTestProvidedDependencies) {
            dependencies.append(dep);
            dependencies.append("\n");
        }

        pom = pom.replace("<!-- DEPENDENCIES -->", dependencies.toString());

        Map<String, String> resolvedProperties = new TreeMap<>();
        Pattern propPattern = Pattern.compile("(\\$\\{[^}]*\\})");
        Matcher m = propPattern.matcher(pom);
        while (m.find()) {
            String property = m.group();
            String resolved = DependencyResolver.resolveParentProperty(property);
            resolvedProperties.put(property, resolved);
        }

        for (String property : resolvedProperties.keySet()) {
            pom = pom.replace(property, resolvedProperties.get(property));
        }

        pom = pom.replace("#{module}", config.getModuleName());

        File pomFile = new File(config.getModuleBasePath() + "/target/itest-spring-boot-pom.xml");
        try (FileWriter fw = new FileWriter(pomFile)) {
            IOUtils.write(pom, fw);
        }

        return pomFile;
    }


    private static ConfigurableMavenResolverSystem resolver(ITestConfig config) {
        return Maven.configureResolver().workOffline(config.getMavenOfflineResolution());
    }

    private static ClassLoader getExtensionClassloader() {
        ClassLoader cl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        return cl;
    }

    private static boolean validTestDependency(ITestConfig config, String dependencyXml, List<MavenDependencyExclusion> exclusions) {

        boolean valid = true;
        for (MavenDependencyExclusion excl : exclusions) {
            String groupId = excl.getGroupId();
            String artifactId = excl.getArtifactId();

            boolean notExclusion = dependencyXml.indexOf("<exclusions>") < 0 || dependencyXml.indexOf(groupId) < dependencyXml.indexOf("<exclusions>");

            if (dependencyXml.contains(groupId) && dependencyXml.contains(artifactId) && notExclusion) {
                valid = false;
                break;
            }
        }

        if (!valid) {
            debug("Discarded test dependency: " + dependencyXml.replace("\n", "").replace("\r", "").replace("\t", ""));
        }

        return valid;
    }

    private static String enforceExclusions(ITestConfig config, String dependencyXml, List<MavenDependencyExclusion> exclusions) {

        if (!dependencyXml.contains("<exclusions>")) {
            dependencyXml = dependencyXml.replace("</dependency>", "<exclusions></exclusions></dependency>");
        }

        for (MavenDependencyExclusion excl : exclusions) {
            String groupId = excl.getGroupId();
            String artifactId = excl.getArtifactId();

            dependencyXml = dependencyXml.replace("</exclusions>", "<exclusion><groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId></exclusion></exclusions>");
        }

        return dependencyXml;
    }

    private static String setResolvedVersion(ITestConfig config, String dependencyXml, Map<String, String> resolvedVersions) throws Exception {

        String groupId = textBetween(dependencyXml, "<groupId>", "</groupId>");
        String artifactId = textBetween(dependencyXml, "<artifactId>", "</artifactId>");

        String resolvedVersion = resolvedVersions.get(groupId + ":" + artifactId);

        if (!dependencyXml.contains("<version>")) {
            String after = "</artifactId>";
            int split = dependencyXml.indexOf(after) + after.length();
            dependencyXml = dependencyXml.substring(0, split) + "<version>" + resolvedVersion + "</version>" + dependencyXml.substring(split);
        } else {
            String versionTag = "<version>";
            int split = dependencyXml.indexOf(versionTag) + versionTag.length();
            int end = dependencyXml.indexOf("</version>");
            dependencyXml = dependencyXml.substring(0, split) + resolvedVersion + dependencyXml.substring(end);
        }

        return dependencyXml;
    }

    private static String textBetween(String text, String start, String end) {
        int sp = text.indexOf(start);
        int rsp = sp + start.length();
        int ep = text.indexOf(end);
        if (sp < 0 || ep < 0 || ep <= rsp) {
            return null;
        }

        String res = text.substring(rsp, ep);
        return res;
    }

    private static boolean excludeDependencyRegex(List<File> dependencies, String regex) {
        Pattern pattern = Pattern.compile(regex);
        int count = 0;
        for (Iterator<File> it = dependencies.iterator(); it.hasNext(); ) {
            File f = it.next();
            if (pattern.matcher(f.getName()).matches()) {
                it.remove();
                count++;
                break;
            }
        }
        return count > 0;
    }

    private static JavaArchive addDependencies(JavaArchive ark, Collection<File> deps) {
        Set<File> dependencySet = new HashSet<>(deps);
        for (File d : dependencySet) {
            debug("Adding spring-boot dependency: " + d.getName());
            ark = ark.add(new FileAsset(d), LIB_FOLDER + "/" + d.getName());
        }

        return ark;
    }

    private static JavaArchive addTestClasses(JavaArchive mainArk, Domain domain, ITestConfig config) throws IOException {

        File test = new File(config.getModuleBasePath() + "/target/test-classes/");
        File[] fs = test.listFiles();
        if (fs == null) {
            fs = new File[]{};
        }
        LinkedList<File> testFiles = new LinkedList<>(Arrays.asList(fs));
        while (!testFiles.isEmpty()) {
            File f = testFiles.pop();
            String relative = test.getCanonicalFile().toURI().relativize(f.getCanonicalFile().toURI()).getPath();
            if (f.isFile()) {
                if (f.getName().endsWith(".class")) {
                    mainArk = mainArk.addAsResource(f, CLASSES_FOLDER + "/" + relative);
                }
            } else {
                mainArk = mainArk.addAsDirectory(CLASSES_FOLDER + "/" + relative);
                File[] files = f.listFiles();
                if (files == null) {
                    files = new File[]{};
                }
                testFiles.addAll(Arrays.asList(files));
            }
        }

        return mainArk;
    }

    private static JavaArchive addSpringbootPackage(JavaArchive ark, String... packageNames) throws Exception {

        Iterable<ClassLoader> classLoaders = Collections.singleton(Thread.currentThread().getContextClassLoader());

        for (String packageName : packageNames) {
            for (final ClassLoader classLoader : classLoaders) {

                final URLPackageScanner.Callback callback = new URLPackageScanner.Callback() {
                    @Override
                    public void classFound(String className) {
                        ArchivePath classNamePath = AssetUtil.getFullPathForClassResource(className);

                        Asset asset = new ClassLoaderAsset(classNamePath.get().substring(1), classLoader);
                        ArchivePath location = new BasicPath(CLASSES_FOLDER + "/", classNamePath);
                        ark.add(asset, location);
                    }
                };
                final URLPackageScanner scanner = URLPackageScanner.newInstance(true, classLoader, callback, packageName);
                scanner.scanPackage();
            }
        }

        return ark;
    }


    private static void debug(String str) {
        if (DEBUG_ENABLED) {
            System.out.println("DEBUG>>> " + str);
        }
    }

}
