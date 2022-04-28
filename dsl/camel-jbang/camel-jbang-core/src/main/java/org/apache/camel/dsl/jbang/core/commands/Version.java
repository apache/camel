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

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceDoesNotExist;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.fetchGithubUrls;

@Command(name = "init", description = "Display Camel version")
class Version implements Callable<Integer> {

    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @Override
    public Integer call() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String v = catalog.getCatalogVersion();
        System.out.println("Camel JBang " + v);
        return 0;
    }

}
