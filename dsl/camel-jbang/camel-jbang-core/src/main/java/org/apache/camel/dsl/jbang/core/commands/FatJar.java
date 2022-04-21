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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import groovy.grape.Grape;
import groovy.lang.GroovyClassLoader;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "fat-jar", description = "Package application as a single fat-jar")
class FatJar implements Callable<Integer> {

    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

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

        // copy settings also (but not as hidden file)
        File out = new File(target, Run.RUN_SETTINGS_FILE.substring(1));
        safeCopy(settings, out, true);

        // routes
        target = new File("target/camel-app/");
        copyFiles(settings, target);

        // log4j configuration
        InputStream is = FatJar.class.getResourceAsStream("/log4j2.properties");
        safeCopy(is, new File(target, "log4j2.properties"), false);

        List<String> lines = Files.readAllLines(settings.toPath());

        // include log4j dependencies
        lines.add("dependency=org.apache.logging.log4j:log4j-api:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-core:2.17.2");
        lines.add("dependency=org.apache.logging.log4j:log4j-slf4j-impl:2.17.2");
        lines.add("dependency=org.fusesource.jansi:jansi:2.4.0");

        // include camel-kamelet-main as its a core dependency needed
        Optional<MavenGav> first = lines.stream()
                .filter(l -> l.startsWith("dependency="))
                .map(l -> MavenGav.parseGav(null, StringHelper.after(l, "dependency=")))
                .filter(g -> "org.apache.camel".equals(g.getGroupId()))
                .findFirst();
        if (first.isPresent()) {
            String v = first.get().getVersion();
            lines.add(0, "dependency=mvn:org.apache.camel:camel-kamelet-main:" + v);
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

        return 0;
    }

    private void copyFiles(File settings, File target) throws Exception {
        // read the settings file and find the files to copy
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));

        String files = prop.getProperty("camel.main.routesIncludePattern");
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
