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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertTrue;

/**
 * Base class for Generator MOJO tests.
 */
public abstract class AbstractGeneratorMojoTest {
    protected static final String OUT_DIR = "target/generated-test-sources/camel-component";

    protected static final String COMPONENT_PACKAGE = "org.apache.camel.component.test";
    protected static final String OUT_PACKAGE = COMPONENT_PACKAGE + ".internal";

    protected static final String PACKAGE_PATH = OUT_PACKAGE.replaceAll("\\.", "/") + "/";
    protected static final String COMPONENT_PACKAGE_PATH = COMPONENT_PACKAGE.replaceAll("\\.", "/") + "/";

    protected static final String COMPONENT_NAME = "Test";
    protected static final String SCHEME = "testComponent";

    protected void assertExists(File outFile) {
        assertTrue("Generated file not found " + outFile.getPath(), outFile.exists());
    }

    protected void configureSourceGeneratorMojo(AbstractSourceGeneratorMojo mojo) {
        configureGeneratorMojo(mojo);
        mojo.generatedSrcDir = new File(OUT_DIR);
        mojo.generatedTestDir = new File(OUT_DIR);
    }

    protected void configureGeneratorMojo(AbstractGeneratorMojo mojo) {
        mojo.componentName = COMPONENT_NAME;
        mojo.scheme = SCHEME;
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

                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getTestSourceDirectory() {
                        return OUT_DIR;
                    }
                };
            }

            @Override
            public String getGroupId() {
                return "org.apache.camel.component";
            }

            @Override
            public String getArtifactId() {
                return "camel-test";
            }

            @Override
            public String getVersion() {
                return "1.0-SNAPSHOT";
            }
        };
    }

    protected AbstractSourceGeneratorMojo createGeneratorMojo() {
        return null;
    }


    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddCompilationRootsByDefault() throws Exception {
        AbstractSourceGeneratorMojo mojo = createGeneratorMojo();
        Assume.assumeThat("Ignored because createGeneratorMojo is not implemented", mojo, notNullValue());
        // Differentiate target folders to simplify assertion
        mojo.generatedSrcDir = new File(OUT_DIR.replace("-test-", ""));
        mojo.generatedTestDir = new File(OUT_DIR);
        mojo.execute();

        assertCompileSourceRoots(mojo.project::getCompileSourceRoots, mojo.generatedSrcDir);
        assertCompileSourceRoots(mojo.project::getTestCompileSourceRoots, mojo.generatedTestDir);
    }

    @Test
    public void shouldAddCompilationRootsByConfiguration() throws Exception {
        File srcDir = new File(OUT_DIR.replace("-test-", ""));
        File testDir = new File(OUT_DIR);
        File[] empty = new File[0];
        assertCompilationRootsByConfiguration(AbstractSourceGeneratorMojo.CompileRoots.source, srcDir, testDir,
            new File[]{srcDir, testDir}, empty);
        assertCompilationRootsByConfiguration(AbstractSourceGeneratorMojo.CompileRoots.test, srcDir, testDir,
            empty, new File[]{srcDir, testDir});
        assertCompilationRootsByConfiguration(AbstractSourceGeneratorMojo.CompileRoots.all, srcDir, testDir,
            new File[]{srcDir}, new File[]{testDir});
        assertCompilationRootsByConfiguration(AbstractSourceGeneratorMojo.CompileRoots.none, srcDir, testDir,
            empty, empty);
    }

    private void assertCompilationRootsByConfiguration(AbstractSourceGeneratorMojo.CompileRoots compileRoots,
                                                       File srcDir, File testDir,
                                                       File[] expectedSource, File[] expectedTest) throws Exception {
        AbstractSourceGeneratorMojo mojo = createGeneratorMojo();
        Assume.assumeThat("Ignored because createGeneratorMojo is not implemented", mojo, notNullValue());
        mojo.generatedSrcDir = srcDir;
        mojo.generatedTestDir = testDir;
        mojo.addCompileSourceRoots = compileRoots;
        mojo.execute();

        assertCompileSourceRoots(mojo.project::getCompileSourceRoots, expectedSource);
        assertCompileSourceRoots(mojo.project::getTestCompileSourceRoots, expectedTest);
    }

    private void assertCompileSourceRoots(Supplier<List<String>> roots, File... expectedSources) {
        List<String> compileSourceRoots = roots.get();
        Assert.assertThat(compileSourceRoots.size(), is(expectedSources.length));
        Assert.assertThat(compileSourceRoots, hasItems(
            Stream.of(expectedSources).map(File::getAbsolutePath).toArray(String[]::new))
        );

    }

}
