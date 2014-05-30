/**
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
package org.apache.camel.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.velocity.VelocityContext;

/**
 * Generates Camel Component based on a collection of APIs.
 */
@Mojo(name = "fromApis", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ApiComponentGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(required = true)
    protected ApiProxy[] apis;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (apis == null || apis.length == 0) {
            throw new MojoExecutionException("One or more API proxies are required");
        }

        // TODO generate Component classes
        // generate ApiCollection
        mergeTemplate(getApiCollectionContext(), getApiCollectionFile(), "/api-collection.vm");
    }

    private VelocityContext getApiCollectionContext() {
        final VelocityContext context = new VelocityContext();
        context.put("componentName", componentName);
        context.put("collectionName", getApiCollectionName());
        context.put("packageName", outPackage);
        context.put("apis", apis);
        context.put("helper", getClass());
        return context;
    }

    private File getApiCollectionFile() {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replaceAll("\\.", File.separator)).append(File.separator);
        fileName.append(getApiCollectionName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private String getApiCollectionName() {
        return componentName + "ApiCollection";
    }

    public static String getApiMethod(String proxyClass) {
        return proxyClass.substring(proxyClass.lastIndexOf('.') + 1) + "ApiMethod";
    }
}
