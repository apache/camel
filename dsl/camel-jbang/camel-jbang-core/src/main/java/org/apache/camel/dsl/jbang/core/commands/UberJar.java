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

@Command(name = "uber-jar", description = "Package application as a single uber-jar")
class UberJar extends CamelCommand {

    private static final String BUILD_DIR = ".camel-jbang/work";
    private static final String CLASSES_DIR = BUILD_DIR + "/classes";
    private static final String LIB_DIR = BUILD_DIR + "/lib";
    private static final String BOOTSTRAP_DIR = BUILD_DIR + "/bootstrap";

    private static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles"
    };

    @CommandLine.Option(names = { "-j", "--jar" }, defaultValue = "camel-runner.jar", description = "Jar filename")
    private String jar = "camel-runner.jar";

    @Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    private boolean fresh;

    public UberJar(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        // the settings file has information what to package in uber-jar so we need to read it from the run command
        File settings = new File(Run.WORK_DIR + "/" + Run.RUN_SETTINGS_FILE);
        if (fresh || !settings.exists()) {
            // allow to automatic build
            System.out.println("Generating fresh run data");
            int silent = runSilently();
            if (silent != 0) {
                return silent;
            }
        } else {
            System.out.println("Reusing existing run data");
        }

        System.out.println("Packaging " + jar);
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

        // resolve all the needed dependencies
        ClassLoader parentCL = KameletMain.class.getClassLoader();
        final GroovyClassLoader gcl = new GroovyClassLoader(parentCL);

        // application sources
        buildDir = new File(CLASSES_DIR);
        buildDir.mkdirs();
        copySourceFiles(settings, buildDir);
        // work sources
        copyWorkFiles(Run.WORK_DIR, buildDir);
        // settings
        copySettings(settings);
        // log4j configuration
        InputStream is = UberJar.class.getResourceAsStream("/log4j2.properties");
        safeCopy(is, new File(CLASSES_DIR, "log4j2.properties"));
        is = UberJar.class.getResourceAsStream("/log4j2.component.properties");
        safeCopy(is, new File(CLASSES_DIR, "log4j2.component.properties"));

        List<String> lines = Files.readAllLines(settings.toPath());
        String version = null;

        // include log4j dependencies
        lines.add("dependency=org.apache.logging.log4j:log4j-api:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-core:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-slf4j-impl:2.17.2");
        lines.add("dependency=org.fusesource.jansi:jansi:2.4.0");
        // nested jar classloader
        lines.add("dependency=com.needhamsoftware.unojar:core:1.0.2");

        // include camel-kamelet-main/camel-uberjar-main as they are needed
        Optional<MavenGav> first = lines.stream()
                .filter(l -> l.startsWith("dependency="))
                .map(l -> MavenGav.parseGav(null, StringHelper.after(l, "dependency=")))
                .filter(g -> "org.apache.camel".equals(g.getGroupId()))
                .findFirst();
        if (first.isPresent()) {
            version = first.get().getVersion();
            lines.add(0, "dependency=mvn:org.apache.camel:camel-kamelet-main:" + version);
            lines.add(0, "dependency=mvn:org.apache.camel:camel-uberjar-main:" + version);
        }
        if (version == null) {
            throw new IllegalStateException("Cannot determine Camel version");
        }

        // JARs should be in lib sub-folder
        buildDir = new File(LIB_DIR);
        buildDir.mkdirs();
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
                copyJars(u, buildDir);
            }
        }

        // MANIFEST.MF
        manifest(version);

        // app sources as classes
        applicationClasses();

        // boostrap classloader
        boostrapClassLoader();

        // and build uber jar
        archiveUberJar();

        // cleanup work folder
        FileUtil.removeDir(new File(BUILD_DIR));

        return 0;
    }

    private Integer runSilently() throws Exception {
        Run run = new Run(getMain());
        Integer code = run.runSilent();
        return code;
    }

    private void copySettings(File settings) throws Exception {
        // the settings file itself
        File setting = new File(CLASSES_DIR, Run.RUN_SETTINGS_FILE);

        // need to adjust file: scheme to classpath as the files are now embedded in the uber-jar directly
        List<String> lines = Files.readAllLines(settings.toPath());
        FileOutputStream fos = new FileOutputStream(setting, false);
        for (String line : lines) {
            if (line.startsWith("camel.main.routesCompileDirectory")) {
                continue; // skip as uber-jar should not compile to disk
            }
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
            // file:foo.java is compiled to .class so we need to replace it
            value = value.replaceAll("file:", "classpath:");
            value = value.replaceAll(".java", ".class");
            // special fix for generated files inside .camel-jbang
            value = value.replaceAll("classpath:.camel-jbang/", "classpath:");
            line = key + "=" + value;
        }
        return line;
    }

    private void boostrapClassLoader() throws Exception {
        File dir = new File(BOOTSTRAP_DIR);
        dir.mkdirs();

        // nested-jar classloader is named core
        File bootstrapJar = new File(LIB_DIR, "/core-1.0.2.jar");

        JarInputStream jis = new JarInputStream(new FileInputStream(bootstrapJar));
        JarEntry je;
        while ((je = jis.getNextJarEntry()) != null) {
            if (!je.isDirectory()) {
                String name = je.getName();
                if (name.endsWith(".class")) {
                    name = BOOTSTRAP_DIR + "/" + name;
                    String path = FileUtil.onlyPath(name);
                    // ensure sub-folders are created
                    new File(path).mkdirs();
                    FileOutputStream fos = new FileOutputStream(name);
                    IOHelper.copy(jis, fos);
                    IOHelper.close(fos);
                }
            }
        }

        // delete to avoid duplicate
        bootstrapJar.delete();
    }

    private void applicationClasses() throws Exception {
        // build application.jar that has the user source
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(LIB_DIR + "/application.jar", false));

        File dir = new File(CLASSES_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    JarEntry je = new JarEntry(f.getName());
                    jos.putNextEntry(je);
                    IOHelper.copyAndCloseInput(new FileInputStream(f), jos);
                }
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

        File f = new File(BUILD_DIR, "META-INF");
        f.mkdirs();
        IOHelper.writeText(context, new FileOutputStream(f + "/MANIFEST.MF", false));
    }

    private void archiveUberJar() throws Exception {
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar, false));

        // include manifest first
        File fm = new File(BUILD_DIR, "META-INF/MANIFEST.MF");
        JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
        jos.putNextEntry(je);
        IOHelper.copyAndCloseInput(new FileInputStream(fm), jos);
        // include boostrap
        for (File fl : new File(BOOTSTRAP_DIR, "com/needhamsoftware/unojar").listFiles()) {
            if (fl.isFile()) {
                je = new JarEntry("com/needhamsoftware/unojar/" + fl.getName());
                jos.putNextEntry(je);
                IOHelper.copyAndCloseInput(new FileInputStream(fl), jos);
            }
        }
        // include JARs
        for (File fl : new File(LIB_DIR).listFiles()) {
            if (fl.isFile()) {
                if (fl.getName().startsWith("camel-uberjar-main")) {
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
                    String scheme = getScheme(f);
                    if (scheme != null) {
                        f = f.substring(scheme.length() + 1);
                    }
                    File source = new File(f);
                    File out = new File(target, source.getName());
                    safeCopy(source, out, true);
                }
            }
        }
    }

    private void copyWorkFiles(String work, File target) throws Exception {
        File[] files = new File(work).listFiles();
        if (files != null) {
            for (File source : files) {
                // only copy files and skip settings file as we do this later specially
                if (source.isDirectory() || source.getName().equals(Run.RUN_SETTINGS_FILE)) {
                    continue;
                }
                File out = new File(target, source.getName());
                safeCopy(source, out, true);
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

    private void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
    }

    private static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

}
