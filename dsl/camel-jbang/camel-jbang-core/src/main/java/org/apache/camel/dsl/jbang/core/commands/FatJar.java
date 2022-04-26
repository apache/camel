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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import groovy.grape.Grape;
import groovy.lang.GroovyClassLoader;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "fat-jar", description = "Package application as a single fat-jar")
class FatJar implements Callable<Integer> {

    private static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[]{
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location"
    };

    //CHECKSTYLE:OFF
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @CommandLine.Option(names = { "-j", "--jar" }, defaultValue = "target/camel-app.jar", description = "Jar filename")
    private String jar = "target/camel-app.jar";

    @Override
    public Integer call() throws Exception {
        File settings = new File(Run.RUN_SETTINGS_FILE);
        if (!settings.exists()) {
            System.out.println("Run Camel first to generate dependency file");
            return 0;
        }

        File target = new File("target/camel-app/");
        FileUtil.removeDir(target);
        target.mkdirs();

        // resolve all the needed dependencies
        ClassLoader parentCL = KameletMain.class.getClassLoader();
        final GroovyClassLoader gcl = new GroovyClassLoader(parentCL);

        // application sources
        target = new File("target/camel-app/classes");
        target.mkdirs();
        copySourceFiles(settings, target);
        // settings
        copySettings(settings);
        // log4j configuration
        InputStream is = FatJar.class.getResourceAsStream("/log4j2.properties");
        safeCopy(is, new File("target/camel-app/classes", "log4j2.properties"), false);

        List<String> lines = Files.readAllLines(settings.toPath());
        String version = null;

        // include log4j dependencies
        lines.add("dependency=org.apache.logging.log4j:log4j-api:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-core:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-slf4j-impl:2.17.2");
        lines.add("dependency=org.fusesource.jansi:jansi:2.4.0");
        // nested jar classloader
        lines.add("dependency=com.needhamsoftware.unojar:core:1.0.2");

        // include camel-kamelet-main/camel-fatjar-main as they are needed
        Optional<MavenGav> first = lines.stream()
                .filter(l -> l.startsWith("dependency="))
                .map(l -> MavenGav.parseGav(null, StringHelper.after(l, "dependency=")))
                .filter(g -> "org.apache.camel".equals(g.getGroupId()))
                .findFirst();
        if (first.isPresent()) {
            version = first.get().getVersion();
            lines.add(0, "dependency=mvn:org.apache.camel:camel-kamelet-main:" + version);
            lines.add(0, "dependency=mvn:org.apache.camel:camel-fatjar-main:" + version);
        }
        if (version == null) {
            throw new IllegalStateException("Cannot determine Camel version");
        }

        // JARs should be in lib sub-folder
        target = new File("target/camel-app/lib");
        target.mkdirs();
        for (String l : lines) {
            if (l.startsWith("dependency=")) {
                l = StringHelper.after(l, "dependency=");
                MavenGav gav = MavenGav.parseGav(null, l);
                Map<String, Object> map = new HashMap<>();
                map.put("classLoader", gcl);
                map.put("group", gav.getGroupId());
                map.put("module", gav.getArtifactId());
                map.put("version", gav.getVersion());
                map.put("classifier", "");

                URI[] u = Grape.resolve(map, map);
                copyJars(u, target);
            }
        }

        // MANIFEST.MF
        manifest(version);

        // app sources as classes
        applicationClasses();

        // boostrap classloader
        boostrapClassLoader();

        // and build target jar
        archiveFatJar();

        return 0;
    }

    private void copySettings(File settings) throws Exception {
        // the settings file itself
        File target = new File("target/camel-app/classes", Run.RUN_SETTINGS_FILE.substring(1));

        // need to adjust file: scheme to classpath as the files are now embedded in the fat-jar directly
        List<String> lines = Files.readAllLines(settings.toPath());
        FileOutputStream fos = new FileOutputStream(target, false);
        for (String line : lines) {
            for (String k : SETTINGS_PROP_SOURCE_KEYS) {
                line = fileToClasspath(line, k);
            }
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.write("\n".getBytes(StandardCharsets.UTF_8));
        }
        IOHelper.close(fos);
    }

    private static String fileToClasspath(String line, String key) {
        String value = StringHelper.after(line, key + "=");
        if (value != null) {
            value = value.replaceAll("file:", "classpath:");
            line = key + "=" + value;
        }
        return line;
    }

    private void boostrapClassLoader() throws Exception {
        File target = new File("target/camel-app/bootstrap");
        target.mkdirs();

        // nested-jar classloader is named core
        File fl = new File("target/camel-app/lib/core-1.0.2.jar");

        JarInputStream jis = new JarInputStream(new FileInputStream(fl));
        JarEntry je;
        while ((je = jis.getNextJarEntry()) != null) {
            if (!je.isDirectory()) {
                String name = je.getName();
                if (name.endsWith(".class")) {
                    // ensure sub-folders are created
                    String path = FileUtil.onlyPath("target/camel-app/bootstrap/" + name);
                    new File(path).mkdirs();
                    FileOutputStream fos = new FileOutputStream("target/camel-app/bootstrap/" + name);
                    IOHelper.copy(jis, fos);
                    IOHelper.close(fos);
                }
            }
        }

        // delete to avoid duplicate
        fl.delete();
    }

    private void applicationClasses() throws Exception {
        // build JAR of target/classes
        JarOutputStream jos = new JarOutputStream(new FileOutputStream("target/camel-app/lib/application.jar", false));

        File dir = new File("target/camel-app/classes");
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                JarEntry je = new JarEntry(f.getName());
                jos.putNextEntry(je);
                IOHelper.copyAndCloseInput(new FileInputStream(f), jos);
            }
        }

        jos.flush();
        IOHelper.close(jos);
    }

    private void manifest(String version) throws Exception {
        InputStream is = Init.class.getClassLoader().getResourceAsStream("templates/manifest.tmpl");
        if (is == null) {
            throw new FileNotFoundException("templates/manifest.tmpl");
        }
        String context = IOHelper.loadText(is);
        IOHelper.close(is);
        context = context.replaceFirst("\\{\\{ \\.Version }}", version);

        File f = new File("target/camel-app/META-INF");
        f.mkdirs();
        IOHelper.writeText(context, new FileOutputStream(f + "/MANIFEST.MF", false));
    }

    private void archiveFatJar() throws Exception {
        // package all inside target/camel-app as a jar-file in target folder
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar, false));

        // include manifest first
        File fm = new File("target/camel-app/META-INF/MANIFEST.MF");
        JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
        jos.putNextEntry(je);
        IOHelper.copyAndCloseInput(new FileInputStream(fm), jos);
        // include boostrap
        for (File fl : new File("target/camel-app/bootstrap/com/needhamsoftware/unojar").listFiles()) {
            if (fl.isFile()) {
                je = new JarEntry("com/needhamsoftware/unojar/" + fl.getName());
                jos.putNextEntry(je);
                IOHelper.copyAndCloseInput(new FileInputStream(fl), jos);
            }
        }
        // include JARs
        for (File fl : new File("target/camel-app/lib/").listFiles()) {
            if (fl.isFile()) {
                if (fl.getName().startsWith("camel-fatjar-main")) {
                    // must be in main folder
                    je = new JarEntry("main/" + fl.getName());
                } else {
                    je = new JarEntry("lib/" + fl.getName());
                }
                jos.putNextEntry(je);
                IOHelper.copyAndCloseInput(new FileInputStream(fl), jos);
            }
        }

        jos.flush();
        IOHelper.close(jos);
    }

    private void copySourceFiles(File settings, File target) throws Exception {
        // read the settings file and find the files to copy
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));

        for (String k : SETTINGS_PROP_SOURCE_KEYS) {
            String files = prop.getProperty(k);
            if (files != null) {
                for (String f : files.split(",")) {
                    if (f.startsWith("file:")) {
                        f = f.substring(5);
                        File source = new File(f);
                        File out = new File(target, source.getName());
                        safeCopy(source, out, true);
                    }
                }
            }
        }
    }

    private void copyJars(URI[] uris, File target) throws Exception {
        for (URI u : uris) {
            File f = new File(u.toURL().getFile());
            File out = new File(target, f.getName());
            safeCopy(f, out, false);
        }
    }

    private void safeCopy(File source, File target, boolean override) throws Exception {
        if (!source.exists()) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath());
        } else if (override) {
            Files.copy(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void safeCopy(InputStream source, File target, boolean override) throws Exception {
        if (source == null) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        } else if (override) {
            Files.copy(source, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
