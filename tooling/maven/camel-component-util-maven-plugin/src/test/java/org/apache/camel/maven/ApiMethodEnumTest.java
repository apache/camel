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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

/**
 * Tests api-method-enum.vm
 */
public class ApiMethodEnumTest {

    @Test
    public void testTemplate() throws IOException, MojoFailureException, MojoExecutionException {

        final FileApiMethodGeneratorMojo mojo = new FileApiMethodGeneratorMojo() {

            @Override
            public List<String> getSignatureList() throws MojoExecutionException {
                final ArrayList<String> signatures = new ArrayList<String>();
                signatures.add("public String sayHi();");
                signatures.add("public String sayHi(final String name);");
                signatures.add("public final String greetMe(final String name);");
                signatures.add("public final String greetUs(final String name1, String name2);");
                signatures.add("public final String greetAll(String[] names);");
                signatures.add("public final String greetAll(java.util.List<String> names);");
                signatures.add("public final String[] greetTimes(String name, int times);");
                return signatures;
            }
        };
        mojo.substitutions = new Substitution[1];
        mojo.substitutions[0] = new Substitution(".+", "(.+)", "java.util.List", "$1List");

        mojo.outDir = new File("target/generated-test-sources/camelComponent");
        mojo.outPackage = "org.apache.camel.component.util";
        mojo.proxyClass = TestProxy.class.getCanonicalName();
        mojo.project = new MavenProject((Model) null) {
            @Override
            public List getRuntimeClasspathElements() throws DependencyResolutionRequiredException {
                return Collections.EMPTY_LIST;
            }
        };

        mojo.execute();
    }

}
