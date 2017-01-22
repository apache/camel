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

import org.apache.camel.component.test.TestProxy;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

/**
 * Tests {@link FileApiMethodGeneratorMojo}
 */
public class FileApiMethodGeneratorMojoTest extends AbstractGeneratorMojoTest {

    @Test
    public void testExecute() throws IOException, MojoFailureException, MojoExecutionException {

        // delete target file to begin
        final File outFile = new File(OUT_DIR, PACKAGE_PATH + "TestProxyApiMethod.java");
        if (outFile.exists()) {
            outFile.delete();
        }

        final FileApiMethodGeneratorMojo mojo = createGeneratorMojo();
        mojo.execute();

        // check target file was generated
        assertExists(outFile);
    }

    protected FileApiMethodGeneratorMojo createGeneratorMojo() {
        final FileApiMethodGeneratorMojo mojo = new FileApiMethodGeneratorMojo();
        mojo.substitutions = new Substitution[2];
        mojo.substitutions[0] = new Substitution(".+", "(.+)", "java.util.List", "$1List", false);
        mojo.substitutions[1] = new Substitution(".+", "(.+)", ".*?(\\w++)\\[\\]", "$1Array", true);
        mojo.extraOptions = new ExtraOption[1];
        mojo.extraOptions[0] = new ExtraOption("java.util.List<String>", "extraStrings");

        configureSourceGeneratorMojo(mojo);
        mojo.proxyClass = TestProxy.class.getCanonicalName();
        mojo.signatureFile = new File("src/test/resources/test-proxy-signatures.txt");

        // exclude name2, and int times
        mojo.excludeConfigNames = "name2";
        mojo.excludeConfigTypes = "int";
        return mojo;
    }

}
