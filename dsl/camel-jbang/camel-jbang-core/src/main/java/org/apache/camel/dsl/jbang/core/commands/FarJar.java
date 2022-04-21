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
import java.net.URI;
import java.nio.file.Files;
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
class FarJar implements Callable<Integer> {

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

        File target = new File("target/camel-app/lib");
        FileUtil.removeDir(target);
        target.mkdirs();

        // resolve all the needed dependencies
        ClassLoader parentCL = KameletMain.class.getClassLoader();
        final GroovyClassLoader gcl = new GroovyClassLoader(parentCL);

        List<String> lines = Files.readAllLines(settings.toPath());

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

        // copy route files
        // TODO: 3rd party .jar should be in lib
        target = new File("target/camel-app/");
        copyFiles(settings, target);

        // copy settings also (but not as hidden file)
        File out = new File(target, Run.RUN_SETTINGS_FILE.substring(1));
        Files.copy(settings.toPath(), out.toPath());

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
                    File s = new File(f);
                    if (s.exists() && s.isFile()) {
                        File out = new File(target, s.getName());
                        Files.copy(s.toPath(), out.toPath());
                    }
                }
            }
        }
    }

    private void copyJars(URI[] uris, File target) throws Exception {
        for (URI u : uris) {
            File f = new File(u.toURL().getFile());
            File out = new File(target, f.getName());
            if (!out.exists()) {
                Files.copy(f.toPath(), out.toPath());
            }
        }
    }

}
