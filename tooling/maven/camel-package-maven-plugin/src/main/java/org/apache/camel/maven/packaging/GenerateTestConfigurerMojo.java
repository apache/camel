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
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generate configurer classes from @Configurer annotated classes for test sources.
 */
@Mojo(name = "generate-test-configurer", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
// must include runtime dependencies to generate configurer source
public class GenerateTestConfigurerMojo extends AbstractGenerateConfigurerMojo {

    /**
     * The output directory for generated test java source code
     */
    @Parameter(defaultValue = "${project.basedir}/src/test/java")
    protected File sourcesOutputDir;

    /**
     * The output directory for generated test resource source code
     */
    @Parameter(defaultValue = "${project.basedir}/src/test/resources")
    protected File resourcesOutputDir;

    /**
     * To generate configurer for these test classes. The syntax is either <tt>fqn</tt> or </tt>fqn=targetFqn</tt>. This
     * allows to map source class to target class to generate the source code using a different classname.
     */
    @Parameter
    protected List<String> classes;

    public GenerateTestConfigurerMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/test/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/test/resources");
        }

        doExecute(sourcesOutputDir, resourcesOutputDir, classes, true);
    }

}
