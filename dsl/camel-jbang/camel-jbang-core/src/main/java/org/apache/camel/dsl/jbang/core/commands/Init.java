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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Stack;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.common.ResourceDoesNotExist;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.fetchGithubUrls;

@Command(name = "init", description = "Creates a new Camel integration",
         sortOptions = false)
public class Init extends CamelCommand {

    @Parameters(description = "Name of integration file (or a github link)", arity = "1",
                paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    private Path filePath; // Defined only for file path completion; the field never used
    private String file;

    @Option(names = {
            "--dir",
            "--directory" }, description = "Directory where the project will be saved", defaultValue = ".")
    private String directory;

    @Option(names = { "--from-kamelet" },
            description = "To be used when extending an existing Kamelet")
    private String fromKamelet;

    @Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    private String kameletsVersion;

    @Option(names = { "--integration" },
            description = "When creating a yaml file should it be created as a Camel K Integration CRD")
    private boolean integration;

    public Init(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // is the file referring to an existing file on github/gist
        // then we should download the file to local for use
        if (file.startsWith("https://github.com/")) {
            return downloadFromGithub();
        } else if (file.startsWith("https://gist.github.com/")) {
            return downloadFromGist();
        }

        String ext = FileUtil.onlyExt(file, false);
        if ("yaml".equals(ext) && integration) {
            ext = "integration.yaml";
        }

        if (fromKamelet != null && !"kamelet.yaml".equals(ext)) {
            System.out.println("When extending from an existing Kamelet then file must have extension .kamelet.yaml");
            return 1;
        }

        String name = FileUtil.onlyName(file, false);
        InputStream is = null;
        if ("kamelet.yaml".equals(ext)) {
            if (fromKamelet != null) {
                if (kameletsVersion == null) {
                    kameletsVersion = VersionHelper.extractKameletsVersion();
                }
                // load existing kamelet
                is = KameletCatalogHelper.loadKameletYamlSchema(fromKamelet, kameletsVersion);
            } else if (file.contains("source")) {
                ext = "kamelet-source.yaml";
            } else if (file.contains("sink")) {
                ext = "kamelet-sink.yaml";
            } else {
                ext = "kamelet-action.yaml";
            }
        } else if (ext != null && (ext.startsWith("camel.yaml") || ext.startsWith("camel.xml"))) {
            // we allow xxx.camel.yaml / xxx.camel.xml
            ext = ext.substring(6);
        }

        if (is == null) {
            is = Init.class.getClassLoader().getResourceAsStream("templates/" + ext + ".tmpl");
        }
        if (is == null) {
            if (fromKamelet != null) {
                System.out.println("Error: Existing Kamelet does not exist: " + fromKamelet);
            } else {
                System.out.println("Error: Unsupported file type: " + ext);
            }
            return 1;
        }
        String content = IOHelper.loadText(is);
        IOHelper.close(is);

        if (!directory.equals(".")) {
            File dir = new File(directory);
            CommandHelper.cleanExportDir(directory);
            // ensure target dir is created after clean
            dir.mkdirs();
        }
        File target = new File(directory, file);
        content = content.replaceFirst("\\{\\{ \\.Name }}", name);
        if (fromKamelet != null) {
            content = content.replaceFirst("\\s\\sname:\\s" + fromKamelet, "  name: " + name);
            content = content.replaceFirst("camel.apache.org/provider: \"Apache Software Foundation\"",
                    "camel.apache.org/provider: \"Custom\"");

            StringBuilder sb = new StringBuilder();
            String[] lines = content.split("\n");
            boolean top = true;
            for (String line : lines) {
                // remove top license header
                if (top && line.startsWith("#")) {
                    continue;
                }
                top = false;
                sb.append(line);
                sb.append("\n");
            }
            content = sb.toString();
        }
        IOHelper.writeText(content, new FileOutputStream(target, false));
        return 0;
    }

    private int downloadFromGithub() throws Exception {
        StringJoiner all = new StringJoiner(",");

        String ext = FileUtil.onlyExt(file);
        boolean wildcard = FileUtil.onlyName(file, false).contains("*");
        if (ext != null && !wildcard) {
            // it is a single file so map to
            String url = asGithubSingleUrl(file);
            all.add(url);
        } else {
            fetchGithubUrls(file, all);
        }

        if (all.length() > 0) {
            // okay we downloaded something so prepare export dir
            if (!directory.equals(".")) {
                File dir = new File(directory);
                CommandHelper.cleanExportDir(directory);
                // ensure target dir is created after clean
                dir.mkdirs();
            }

            CamelContext tiny = new DefaultCamelContext();
            GitHubResourceResolver resolver = new GitHubResourceResolver();
            resolver.setCamelContext(tiny);
            for (String u : all.toString().split(",")) {
                Resource resource = resolver.resolve(u);
                if (!resource.exists()) {
                    throw new ResourceDoesNotExist(resource);
                }
                String loc = resource.getLocation();
                String name = FileUtil.stripPath(loc);
                File target = new File(directory, name);
                try (FileOutputStream fo = new FileOutputStream(target)) {
                    IOUtils.copy(resource.getInputStream(), fo);
                }
            }
        }

        return 0;
    }

    private Integer downloadFromGist() throws Exception {
        StringJoiner all = new StringJoiner(",");

        fetchGistUrls(file, all);

        if (all.length() > 0) {
            // okay we downloaded something so prepare export dir
            if (!directory.equals(".")) {
                File dir = new File(directory);
                CommandHelper.cleanExportDir(directory);
                // ensure target dir is created after clean
                dir.mkdirs();
            }

            CamelContext tiny = new DefaultCamelContext();
            GistResourceResolver resolver = new GistResourceResolver();
            resolver.setCamelContext(tiny);
            for (String u : all.toString().split(",")) {
                Resource resource = resolver.resolve(u);
                if (!resource.exists()) {
                    throw new ResourceDoesNotExist(resource);
                }
                String loc = resource.getLocation();
                String name = FileUtil.stripPath(loc);
                File target = new File(directory, name);
                try (FileOutputStream fo = new FileOutputStream(target)) {
                    IOUtils.copy(resource.getInputStream(), fo);
                }
            }
        }

        return 0;
    }

    static class FileConsumer extends ParameterConsumer<Init> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Init cmd) {
            cmd.file = args.pop();
        }
    }

}
