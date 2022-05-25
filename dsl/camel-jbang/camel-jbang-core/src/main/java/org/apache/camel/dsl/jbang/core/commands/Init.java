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

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceDoesNotExist;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.apache.camel.dsl.jbang.core.commands.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.fetchGithubUrls;

@Command(name = "init", description = "Initialize empty Camel integration")
class Init extends CamelCommand {

    @CommandLine.Parameters(description = "Name of integration file", arity = "1")
    private String file;

    @Option(names = { "--integration" },
            description = "When creating a yaml file should it be created as a Camel K Integration CRD")
    private boolean integration;

    public Init(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {

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

        String name = FileUtil.onlyName(file, false);
        InputStream is = Init.class.getClassLoader().getResourceAsStream("templates/" + ext + ".tmpl");
        if (is == null) {
            System.out.println("Error: unsupported file type: " + ext);
            return 1;
        }
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.Name }}", name);
        IOHelper.writeText(context, new FileOutputStream(file, false));
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
            CamelContext tiny = new LightweightCamelContext();
            GitHubResourceResolver resolver = new GitHubResourceResolver();
            resolver.setCamelContext(tiny);
            for (String u : all.toString().split(",")) {
                Resource resource = resolver.resolve(u);
                if (!resource.exists()) {
                    throw new ResourceDoesNotExist(resource);
                }

                String loc = resource.getLocation();
                String name = FileUtil.stripPath(loc);

                try (FileOutputStream fo = new FileOutputStream(name)) {
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
            CamelContext tiny = new LightweightCamelContext();
            GistResourceResolver resolver = new GistResourceResolver();
            resolver.setCamelContext(tiny);
            for (String u : all.toString().split(",")) {
                Resource resource = resolver.resolve(u);
                if (!resource.exists()) {
                    throw new ResourceDoesNotExist(resource);
                }

                String loc = resource.getLocation();
                String name = FileUtil.stripPath(loc);

                try (FileOutputStream fo = new FileOutputStream(name)) {
                    IOUtils.copy(resource.getInputStream(), fo);
                }
            }
        }

        return 0;
    }

}
