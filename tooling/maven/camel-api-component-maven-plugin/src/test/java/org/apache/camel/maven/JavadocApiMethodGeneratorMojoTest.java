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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.VelocityContext;
import org.junit.Test;

/**
 * Tests {@link JavadocApiMethodGeneratorMojo}
 */
public class JavadocApiMethodGeneratorMojoTest extends AbstractGeneratorMojoTest {

    @Test
    public void testExecute() throws IOException, MojoFailureException, MojoExecutionException {

        // delete target file to begin
        final File outFile = new File(OUT_DIR, PACKAGE_PATH + "VelocityContextApiMethod.java");
        if (outFile.exists()) {
            outFile.delete();
        }

        final JavadocApiMethodGeneratorMojo mojo = createGeneratorMojo();
        mojo.execute();

        // check target file was generated
        assertExists(outFile);
    }

    @Override
    protected JavadocApiMethodGeneratorMojo createGeneratorMojo() {
        final JavadocApiMethodGeneratorMojo mojo = new JavadocApiMethodGeneratorMojo();

        configureSourceGeneratorMojo(mojo);

        // use VelocityEngine javadoc
        mojo.proxyClass = VelocityContext.class.getCanonicalName();
        Substitution substitution = new Substitution(".*", "key", "java.lang.Object", "applicationKey", false);
        mojo.substitutions = new Substitution[] {substitution};
        mojo.extraOptions = new ExtraOption[1];
        mojo.extraOptions[0] = new ExtraOption("java.util.Map<String, String>", "extraMap");

        mojo.excludePackages = JavadocApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES;
        mojo.includeMethods = ".+";
        mojo.excludeMethods = "clone|Current|internal|icache";
        return mojo;
    }
}
