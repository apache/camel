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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.itest.springboot.ITestConfig;
import org.apache.camel.itest.springboot.arquillian.SpringBootZipExporterImpl;
import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.ExtensionLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
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

        ark = ark.addAsDirectories("/lib");

        String version = config.getMavenVersion();
        if (version == null) {
            // It is missing when launching from IDE
            List<MavenResolvedArtifact> resolved = Arrays.asList(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeDependencies().resolve().withTransitivity().asResolvedArtifact());
            Optional<MavenResolvedArtifact> camelDep = resolved.stream().filter(dep -> dep.getCoordinate().getGroupId().equals("org.apache.camel")).findAny();
            version = camelDep.map(art -> art.getCoordinate().getVersion()).orElse(null);
            debug("Resolved version: " + version);
            if (version == null) {
                throw new IllegalStateException("Cannot determine the current version of the camel component");
            }
        }

        // Test dependencies
        List<MavenDependency> testDependencies = new LinkedList<>();
        if (config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {

            List<MavenResolvedArtifact> testArtifacts = Arrays.asList(Maven.resolver()
                    .loadPomFromFile(config.getModulesPath() + config.getModuleName() + "/pom.xml")
                    .importTestDependencies()
                    .resolve().withoutTransitivity().asResolvedArtifact());

            MavenDependencyExclusion[] excl = new MavenDependencyExclusion[]{MavenDependencies.createExclusion("org.slf4j", "slf4j-log4j12"), MavenDependencies.createExclusion("log4j", "log4j")};

            testDependencies = testArtifacts.stream()
                    .map(MavenResolvedArtifact::getCoordinate)
                    .filter(ArquillianPackager::validTestDependency) // remove direct logging dependencies from test libs
                    .map(c -> MavenDependencies.createDependency(c, ScopeType.RUNTIME, false, excl)) // remove transitive logging dependencies from test libs
                    .collect(Collectors.toList());
        }


        MavenCoordinate jar = MavenCoordinates.createCoordinate(config.getMavenGroup(), config.getModuleName(), version, PackagingType.JAR, null);
        MavenDependency dep = MavenDependencies.createDependency(jar, ScopeType.COMPILE, false);

        List<File> dependencies = new LinkedList<>();
        dependencies.addAll(Arrays.asList(Maven.resolver()
                .loadPomFromFile("pom.xml")
                .importRuntimeDependencies()
                .addDependencies(dep)
                .addDependencies(testDependencies)
                .resolve()
                .withTransitivity()
                .asFile()));


        // The spring boot-loader dependency will be added to the main jar, so it should be excluded from the embedded ones
        excludeDependencyRegex(dependencies, "^spring-boot-loader-[0-9].*");

        // Add all dependencies as spring-boot nested jars
        ark = addDependencies(ark, dependencies);

        if (config.getUnitTestEnabled()) {
            // Add unit test classes of the module under test
            ark = addTestResources(ark, config);
        }

        // Add common packages to main jar
        ark = ark.addPackages(true, "org.apache.camel.itest.springboot");
        ark = ark.addPackages(true, "org.springframework.boot.loader");
        ark = ark.addPackages(true, "org.jboss.shrinkwrap");

        return ClassPath.builder().add(ark).build();
    }

    public static void copyResource(String folder, String fileNameRegex, String targetFolder) throws IOException {

        Pattern pattern = Pattern.compile(fileNameRegex);

        File sourceFolder = new File(folder);
        File[] candidates = sourceFolder.listFiles((dir, name) -> pattern.matcher(name).matches());
        if (candidates.length == 0) {
            Assert.fail("No file matching regex " + fileNameRegex + " has been found");
        }

        File f = candidates[0];
        FileUtils.copyFileToDirectory(f, new File(targetFolder));
    }

    private static ClassLoader getExtensionClassloader() {
        ClassLoader cl = AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        return cl;
    }

    private static boolean validTestDependency(MavenCoordinate coordinate) {

        Pattern log4j = Pattern.compile("^log4j$");
        Pattern slf4jLog4j = Pattern.compile("^slf4j-log4j12$");

        boolean valid = !log4j.matcher(coordinate.getArtifactId()).matches() && !slf4jLog4j.matcher(coordinate.getArtifactId()).matches();

        if (!valid) {
            debug("Discarded test dependency " + coordinate.toCanonicalForm());
        }

        return valid;
    }

    private static void excludeDependencyRegex(List<File> dependencies, String regex) {
        Pattern pattern = Pattern.compile(regex);
        for (Iterator<File> it = dependencies.iterator(); it.hasNext();) {
            File f = it.next();
            if (pattern.matcher(f.getName()).matches()) {
                it.remove();
                break;
            }
        }
    }

    private static JavaArchive addDependencies(JavaArchive ark, Collection<File> deps) {
        Set<File> dependencySet = new HashSet<>(deps);
        for (File d : dependencySet) {
            debug("Adding spring-boot dependency: " + d.getName());
            ark = ark.add(new FileAsset(d), "/lib/" + d.getName());
        }

        return ark;
    }

    private static JavaArchive addTestResources(JavaArchive ark, ITestConfig config) throws IOException {
        File test = new File(config.getModulesPath() + config.getModuleName() + "/target/test-classes/");
        File[] fs = Optional.ofNullable(test.listFiles()).orElse(new File[]{});
        LinkedList<File> testFiles = new LinkedList<>(Arrays.asList(fs));
        while (!testFiles.isEmpty()) {
            File f = testFiles.pop();
            String relative = test.getCanonicalFile().toURI().relativize(f.getCanonicalFile().toURI()).getPath();
            if (f.isFile()) {
                ark = ark.addAsResource(f, relative);
            } else {
                ark = ark.addAsDirectory(relative);
                File[] files = Optional.ofNullable(f.listFiles()).orElse(new File[]{});
                testFiles.addAll(Arrays.asList(files));
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
