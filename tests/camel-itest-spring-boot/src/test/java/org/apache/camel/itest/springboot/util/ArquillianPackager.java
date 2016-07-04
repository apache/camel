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
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.itest.springboot.ITestConfig;
import org.apache.camel.itest.springboot.arquillian.SpringBootZipExporterImpl;
import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.ExtensionLoader;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
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
        ark = ark.addAsResource("spring-boot-itest.properties");

        if (config.getUseCustomLog()) {
            ark = ark.addAsResource("spring-logback.xml");
        }

        for (Map.Entry<String, String> res : config.getResources().entrySet()) {
            ark = ark.addAsResource(res.getKey(), res.getValue());
        }

        ark = ark.addAsDirectories("/lib");

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
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-log4j12"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "log4j"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-simple"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-simple"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-jdk14"));

        for (String ex : config.getMavenExclusions()) {
            commonExclusions.add(MavenDependencies.createExclusion(ex));
        }

        MavenDependencyExclusion[] commonExclutionArray = commonExclusions.toArray(new MavenDependencyExclusion[]{});


        // Module dependencies
        List<MavenDependency> moduleDependencies = new LinkedList<>();

        MavenCoordinate mainJar = MavenCoordinates.createCoordinate(config.getMavenGroup(), config.getModuleName(), version, PackagingType.JAR, null);
        MavenDependency mainDep = MavenDependencies.createDependency(mainJar, ScopeType.COMPILE, false, commonExclutionArray);
        moduleDependencies.add(mainDep);

        for (String canonicalForm : config.getAdditionalDependencies()) {
            MavenCoordinate coord = MavenCoordinates.createCoordinate(canonicalForm);
            MavenDependency dep = MavenDependencies.createDependency(coord, ScopeType.RUNTIME, false);
            moduleDependencies.add(dep);
        }

        if (config.getIncludeProvidedDependencies() || config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {

            List<ScopeType> scopes = new LinkedList<>();
            if (config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {
                scopes.add(ScopeType.TEST);
            }
            if (config.getIncludeProvidedDependencies()) {
                scopes.add(ScopeType.PROVIDED);
            }

            List<MavenResolvedArtifact> moduleArtifacts = Arrays.asList(resolver(config)
                    .loadPomFromFile(config.getModuleBasePath() + "/pom.xml")
                    .importDependencies(scopes.toArray(new ScopeType[]{}))
                    .resolve().withoutTransitivity().asResolvedArtifact());


            for (MavenResolvedArtifact art : moduleArtifacts) {
                MavenCoordinate c = art.getCoordinate();
                if (!validTestDependency(c)) {
                    continue;
                }
                MavenDependency dep = MavenDependencies.createDependency(c, ScopeType.RUNTIME, false, commonExclutionArray);
                moduleDependencies.add(dep);
            }
        }

        List<File> dependencies = new LinkedList<>();
        dependencies.addAll(Arrays.asList(resolver(config)
                .loadPomFromFile("pom.xml")
                .importRuntimeDependencies()
                .addDependencies(moduleDependencies)
                .resolve()
                .withTransitivity()
                .asFile()));


        boolean needsSpringTest = excludeDependencyRegex(dependencies, "^camel-test-spring3-.*");
        if (needsSpringTest) {
            // Adding spring4 version of the test library
            MavenDependency dep = MavenDependencies.createDependency("org.apache.camel:camel-test-spring:" + version, ScopeType.RUNTIME, false);

            dependencies = new LinkedList<>();
            dependencies.addAll(Arrays.asList(resolver(config)
                    .loadPomFromFile("pom.xml")
                    .importRuntimeDependencies()
                    .addDependencies(moduleDependencies)
                    .addDependencies(dep)
                    .resolve()
                    .withTransitivity()
                    .asFile()));
        }

        // The spring boot-loader dependency will be added to the main jar, so it should be excluded from the embedded ones
        excludeDependencyRegex(dependencies, "^spring-boot-loader-[0-9].*");
        excludeDependencyRegex(dependencies, "^camel-test-spring3-.*");


        // Add all dependencies as spring-boot nested jars
        ark = addDependencies(ark, dependencies);

        // Add common packages to main jar
        ark = ark.addPackages(true, "org.apache.camel.itest.springboot");
        ark = ark.addPackages(true, "org.springframework.boot.loader");
        ark = ark.addPackages(true, "org.jboss.shrinkwrap");

        ark = ark.addPackages(true, "org.apache.camel.converter.myconverter"); // to overcome CAMEL-10060
        ark = ark.addPackages(true, "org.apache.camel.osgi.test"); // to overcome CAMEL-10060

        ClassPath.Builder external = ClassPath.builder().add(ark);

        // overcome limitations of some JDKs
        external.addSystemProperty("javax.xml.accessExternalDTD", "all");
        external.addSystemProperty("javax.xml.accessExternalSchema", "all");

        if (config.getUnitTestEnabled()) {
            external.addSystemProperty("container.user.dir", new File(config.getModuleBasePath()).getCanonicalPath());
            external.addSystemProperty("container.test.resources.dir", new File(config.getModuleBasePath()).getCanonicalPath() + "/target/test-classes");
        }

        for (Map.Entry<String, String> e : config.getSystemProperties().entrySet()) {
            external.addSystemProperty(e.getKey(), e.getValue());
        }

        return external.build();
    }

    private static ConfigurableMavenResolverSystem resolver(ITestConfig config) {
        return Maven.configureResolver().workOffline(config.getMavenOfflineResolution());
    }

    public static void copyResource(String folder, String fileNameRegex, String targetFolder) throws IOException {

        final Pattern pattern = Pattern.compile(fileNameRegex);

        File sourceFolder = new File(folder);
        File[] candidates = sourceFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return pattern.matcher(name).matches();
            }
        });
        if (candidates.length == 0) {
            Assert.fail("No file matching regex " + fileNameRegex + " has been found");
        }

        File f = candidates[0];
        FileUtils.copyFileToDirectory(f, new File(targetFolder));
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

    private static boolean validTestDependency(MavenCoordinate coordinate) {

        Pattern[] patterns = new Pattern[]{Pattern.compile("^log4j$"), Pattern.compile("^slf4j-log4j12$"), Pattern.compile("^slf4j-simple$"), Pattern.compile("^slf4j-jdk14$")};

        boolean valid = true;
        for (Pattern p : patterns) {
            if (p.matcher(coordinate.getArtifactId()).matches()) {
                valid = false;
                break;
            }
        }

        if (!valid) {
            debug("Discarded test dependency " + coordinate.toCanonicalForm());
        }

        return valid;
    }

    private static boolean excludeDependencyRegex(List<File> dependencies, String regex) {
        Pattern pattern = Pattern.compile(regex);
        int count = 0;
        for (Iterator<File> it = dependencies.iterator(); it.hasNext();) {
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
            ark = ark.add(new FileAsset(d), "/lib/" + d.getName());
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
                    mainArk = mainArk.addAsResource(f, relative);
                }
            } else {
                mainArk = mainArk.addAsDirectory(relative);
                File[] files = f.listFiles();
                if (files == null) {
                    files = new File[]{};
                }
                testFiles.addAll(Arrays.asList(files));
            }
        }

        return mainArk;
    }

    private static GenericArchive addSources(GenericArchive ark, ITestConfig config) throws IOException {
        File sources = new File(config.getModuleBasePath() + "/src/");
        ark.addAsDirectory("src");

        File[] fs = sources.listFiles();
        if (fs == null) {
            fs = new File[]{};
        }
        LinkedList<File> sourceFiles = new LinkedList<>(Arrays.asList(fs));
        while (!sourceFiles.isEmpty()) {
            File f = sourceFiles.pop();
            String relative = sources.getParentFile().getCanonicalFile().toURI().relativize(f.getCanonicalFile().toURI()).getPath();
            if (f.isFile()) {
                ark.add(new FileAsset(f), relative);
            } else {
                ark = ark.addAsDirectory(relative);
                File[] files = f.listFiles();
                if (files == null) {
                    files = new File[]{};
                }
                sourceFiles.addAll(Arrays.asList(files));
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
