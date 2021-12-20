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
import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceAlreadyExists;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.main.KameletMain;
import org.apache.camel.spi.Resource;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractInitKamelet {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractInitKamelet.class);

    private String resourceLocation;
    private String branch;

    protected void setResourceLocation(String baseResourceLocation, String resourcePath) {
        this.resourceLocation = baseResourceLocation + ":" + resourcePath;
        LOG.debug("Resource location is: {}", resourceLocation);
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    protected File resolveResource(File destinationDirectory) throws IOException, CamelException {
        KameletMain main = new KameletMain();
        main.start();

        CamelContext context = main.getCamelContext();

        try (GitHubResourceResolver resolver = new GitHubResourceResolver()) {
            resolver.setCamelContext(context);
            resolver.setBranch(branch);

            Resource resource = resolver.resolve(resourceLocation);
            if (!resource.exists()) {
                throw new CamelException("The resource does not exist");
            }

            String fileName = FilenameUtils.getName(resource.getURL().getPath());
            LOG.debug("Destination directory for the downloaded resources: {}", destinationDirectory.getAbsolutePath());
            LOG.debug("Downloaded resource file name: {}", fileName);
            File outputFile = new File(destinationDirectory, fileName);

            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    LOG.warn("Failed to create the output directory: {}. It may have been created already", parentDir);
                }
            }

            if (outputFile.exists()) {
                throw new ResourceAlreadyExists(outputFile);
            } else {
                try (FileOutputStream fo = new FileOutputStream(outputFile)) {
                    IOUtils.copy(resource.getInputStream(), fo);
                }
            }

            return outputFile;
        }
    }

    protected void bootstrap(String branch, String baseResourceLocation, String destination)
            throws IOException, CamelException {
        setBranch(branch);
        setResourceLocation(baseResourceLocation, "camel-kamelets:templates/init-template.properties");
        resolveResource(new File(destination));
    }
}
