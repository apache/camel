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
import java.lang.reflect.Method;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Generates the model of the chaos rules of camel-chaos ({@code META-INF/org/apache/camel/chaos/chaos-model.json}): the
 * targets, hooks, faults, parameters, options and recipes, for the tools that help to write rules. The model is made by
 * {@code ChaosModel} of camel-chaos from the tables of its rule parser, so this goal loads that class from the compiled
 * classes of the module and writes what its {@code toJson()} returns, into {@code src/generated/resources} and the
 * output directory. The catalog copies the file ({@code CamelCatalog.chaosModelJSon()}).
 */
@Mojo(name = "generate-chaos-model", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateChaosModelMojo extends AbstractGeneratorMojo {

    /** Where the model is, under the resources directories. */
    public static final String FILE = "META-INF/org/apache/camel/chaos/chaos-model.json";

    /** The class that makes the model: a static {@code toJson()} that returns it. */
    @Parameter(defaultValue = "org.apache.camel.component.chaos.ChaosModel")
    protected String modelClass;

    /** The output directory for the generated resources. */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Inject
    public GenerateChaosModelMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String json;
        try {
            Class<?> type = getProjectClassLoader().loadClass(modelClass);
            Method toJson = type.getDeclaredMethod("toJson");
            // the model is internal to camel-chaos (package-private)
            toJson.setAccessible(true);
            json = (String) toJson.invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new MojoExecutionException("Cannot make the chaos model with " + modelClass + ".toJson()", e);
        }
        if (updateResource(resourcesOutputDir.toPath(), FILE, json)) {
            getLog().info("Updated the chaos model: " + FILE);
        }
    }
}
