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

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for API based code generation MOJOs.
 */
public abstract class AbstractSourceGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/camel-component")
    protected File generatedSrcDir;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/camel-component")
    protected File generatedTestDir;

    enum CompileRoots {
        source, test, all, none
    }

    @Parameter(defaultValue = "all", property = PREFIX + "addCompileSourceRoots")
    protected CompileRoots addCompileSourceRoots = CompileRoots.all;

    protected void setCompileSourceRoots() {
        switch (addCompileSourceRoots) {
        case source:
            project.addCompileSourceRoot(generatedSrcDir.getAbsolutePath());
            project.addCompileSourceRoot(generatedTestDir.getAbsolutePath());
            break;
        case test:
            project.addTestCompileSourceRoot(generatedSrcDir.getAbsolutePath());
            project.addTestCompileSourceRoot(generatedTestDir.getAbsolutePath());
            break;
        case all:
            project.addCompileSourceRoot(generatedSrcDir.getAbsolutePath());
            project.addTestCompileSourceRoot(generatedTestDir.getAbsolutePath());
            break;
        default:
        }
    }

}
