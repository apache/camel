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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Copy test-infra metadata.json into the catalog
 */
@Mojo(name = "update-test-infra-metadata", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class UpdateTestInfraMetadataMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File propertiesDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    protected UpdateTestInfraMetadataMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File testInfraAll = findCamelDirectory(baseDir, "test-infra/camel-test-infra-all");
        if (testInfraAll == null) {
            getLog().debug("No test-infra/camel-test-infra-all folder found, skipping execution");
            return;
        }

        File source = new File(testInfraAll, "src/generated/resources/META-INF/metadata.json");
        File target = new File(propertiesDir, "test-infra/metadata.json");

        try {
            FileUtil.updateFile(source.toPath(), target.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Error updating camel-catalog", e);
        }
    }
}
