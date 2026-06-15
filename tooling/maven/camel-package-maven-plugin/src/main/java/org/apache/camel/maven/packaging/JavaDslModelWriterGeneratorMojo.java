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
import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Generate Java DSL Model Writer that produces fluent Java DSL source code from Camel model definitions.
 */
@Mojo(name = "generate-java-dsl-writer", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class JavaDslModelWriterGeneratorMojo extends ModelWriterGeneratorMojo {

    public static final String WRITER_PACKAGE = "org.apache.camel.java.out";

    @Parameter(defaultValue = "${camel-generate-java-dsl-writer}")
    protected boolean generateJavaDslWriter;

    @Inject
    public JavaDslModelWriterGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        generateJavaDslWriter
                = Boolean.parseBoolean(project.getProperties().getProperty("camel-generate-java-dsl-writer", "false"));
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!generateJavaDslWriter) {
            return;
        }
        Path javaDir = sourcesOutputDir.toPath();
        String writer = generateWriter();
        updateResource(javaDir, (getWriterPackage() + ".JavaDslModelWriter").replace('.', '/') + ".java", writer);
    }

    @Override
    String getWriterPackage() {
        return WRITER_PACKAGE;
    }

    @Override
    protected String getTemplateName() {
        return "velocity/model-java-dsl-writer.vm";
    }
}
