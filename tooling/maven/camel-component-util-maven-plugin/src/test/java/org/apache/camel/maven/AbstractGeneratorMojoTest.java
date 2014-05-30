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
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import static org.junit.Assert.assertTrue;

/**
 * Base class for Generator MOJO tests.
 */
public class AbstractGeneratorMojoTest {
    protected static final String OUT_DIR = "target/generated-test-sources/camel-component";

    private static final String COMPONENT_PACKAGE = "org.apache.camel.component.test";
    private static final String OUT_PACKAGE = COMPONENT_PACKAGE + ".internal";

    protected static final String PACKAGE_PATH = OUT_PACKAGE.replaceAll("\\.", "/") + "/";
    protected static final String COMPONENT_PACKAGE_PATH = COMPONENT_PACKAGE.replaceAll("\\.", "/") + "/";

    protected static final String COMPONENT_NAME = "Test";
    protected static final String SCHEME = "testComponent";

    protected void assertExists(File outFile) {
        assertTrue("Generated file not found " + outFile.getPath(), outFile.exists());
    }

    protected void configureMojo(AbstractGeneratorMojo mojo) {
        mojo.componentName = COMPONENT_NAME;
        mojo.scheme = SCHEME;
        mojo.generatedSrcDir = new File(OUT_DIR);
        mojo.generatedTestDir = new File(OUT_DIR);
        mojo.outPackage = OUT_PACKAGE;
        mojo.componentPackage = COMPONENT_PACKAGE;
        mojo.project = new MavenProject((Model) null) {
            @Override
            public List getTestClasspathElements() throws DependencyResolutionRequiredException {
                return Collections.EMPTY_LIST;
            }

            @Override
            public Build getBuild() {
                return new Build() {
                    @Override
                    public String getTestSourceDirectory() {
                        return OUT_DIR;
                    }
                };
            }
        };
    }
}
