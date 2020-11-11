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
package org.apache.camel.maven;

import java.io.File;

import org.apache.camel.component.test.TestProxy;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ApiComponentGeneratorMojo} for javasource parser
 */
public class ApiJavaSourceComponentGeneratorMojoTest extends AbstractGeneratorMojoTest {

    @Test
    public void testExecute() throws Exception {

        final File collectionFile = new File(OUT_DIR, PACKAGE_PATH + COMPONENT_NAME + "ApiCollection.java");

        // delete target files to begin
        collectionFile.delete();

        final ApiComponentGeneratorMojo mojo = createGeneratorMojo();

        mojo.execute();

        // check target file was generated
        assertExists(collectionFile);
    }

    @Override
    protected ApiComponentGeneratorMojo createGeneratorMojo() {
        final ApiComponentGeneratorMojo mojo = new ApiComponentGeneratorMojo();
        configureSourceGeneratorMojo(mojo);

        mojo.apis = new ApiProxy[1];
        mojo.apis[0] = new ApiProxy();
        mojo.apis[0].setApiName("test");
        mojo.apis[0].setProxyClass(TestProxy.class.getName());
        final FromJavasource fromJavasource = new FromJavasource();
        fromJavasource.setExcludePackages(JavaSourceApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES);
        mojo.apis[0].setFromJavasource(fromJavasource);

        return mojo;
    }

}
