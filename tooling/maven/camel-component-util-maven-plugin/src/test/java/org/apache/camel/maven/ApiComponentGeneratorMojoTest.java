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

import org.apache.camel.util.FileUtil;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

/**
 * Tests {@link ApiComponentGeneratorMojo}
 */
public class ApiComponentGeneratorMojoTest extends AbstractGeneratorMojoTest {

    private static final String COMPONENT_NAME = "TestComponent";

    @Test
    public void testExecute() throws Exception {

        // delete target files to begin
        final File outDir = new File(OUT_DIR);
        FileUtil.removeDir(outDir);

        final File collectionFile = new File(OUT_DIR, PACKAGE_PATH + COMPONENT_NAME + "ApiCollection.java");

        final ApiComponentGeneratorMojo mojo = new ApiComponentGeneratorMojo();
        mojo.componentName = COMPONENT_NAME;
        mojo.outDir = new File(OUT_DIR);
        mojo.outPackage = AbstractGeneratorMojo.OUT_PACKAGE;
        mojo.project = new MavenProject((Model) null) {
            @Override
            public List getRuntimeClasspathElements() throws DependencyResolutionRequiredException {
                return Collections.EMPTY_LIST;
            }
        };

        final ApiProxy[] proxies = new ApiProxy[2];
        mojo.apis = proxies;
        ApiProxy apiProxy = new ApiProxy();
        proxies[0] = apiProxy;
        apiProxy.setApiName("test");
        apiProxy.setProxyClass(TestProxy.class.getName());
        apiProxy.setSignatureFile(new File("src/test/resources/test-proxy-signatures.txt"));
        Substitution[] substitutions = new Substitution[1];
        substitutions[0] = new Substitution(".+", "(.+)", "java.util.List", "$1List");
        apiProxy.setSubstitutions(substitutions);

        apiProxy = new ApiProxy();
        proxies[1] = apiProxy;
        apiProxy.setApiName("velocity");
        apiProxy.setProxyClass(VelocityEngine.class.getName());

        mojo.execute();

        // check target file was generated
        assertExists(collectionFile);
    }
}
