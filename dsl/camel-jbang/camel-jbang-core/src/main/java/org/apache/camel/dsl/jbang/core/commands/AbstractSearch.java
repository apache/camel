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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.api.Extractor;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.main.KameletMain;
import org.apache.camel.spi.Resource;
import org.apache.commons.io.IOUtils;

public abstract class AbstractSearch {
    private String resourceLocation;
    private Pattern pattern;

    // Only used for the search subcommand
    protected AbstractSearch() {
    }

    public AbstractSearch(String resourceLocation, Pattern pattern) {
        this.resourceLocation = resourceLocation;
        this.pattern = pattern;
    }

    protected void downloadResource(File indexFile) throws Exception {
        KameletMain main = new KameletMain();
        main.start();
        CamelContext context = main.getCamelContext();

        try (GitHubResourceResolver resolver = new GitHubResourceResolver()) {
            resolver.setCamelContext(context);

            Resource resource = resolver.resolve(resourceLocation);

            if (!resource.exists()) {
                throw new Exception("The resource does not exist");
            }

            try (FileOutputStream fo = new FileOutputStream(indexFile)) {
                IOUtils.copy(resource.getInputStream(), fo);
            }
        }
    }

    private void readFileByLine(File indexFile, Extractor extractor) throws IOException {
        FileReader indexFileReader = new FileReader(indexFile);
        try (BufferedReader br = new BufferedReader(indexFileReader)) {

            String line;
            do {
                line = br.readLine();

                if (line == null) {
                    break;
                }

                extractor.extract(line);

            } while (line != null);
        }
    }

    public abstract void printHeader();

    public void search(Extractor extractor) throws Exception {
        File indexFile = getIndexFile();

        printHeader();

        readFileByLine(indexFile, extractor);
    }

    private File getIndexFile() throws Exception {
        File indexFile = new File("index");
        indexFile.deleteOnExit();

        downloadResource(indexFile);

        return indexFile;
    }
}
