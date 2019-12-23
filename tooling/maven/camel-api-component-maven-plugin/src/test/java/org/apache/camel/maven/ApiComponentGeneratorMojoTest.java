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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.test.TestProxy;
import org.apache.velocity.VelocityContext;
import org.junit.Test;

/**
 * Tests {@link ApiComponentGeneratorMojo}
 */
public class ApiComponentGeneratorMojoTest extends AbstractGeneratorMojoTest {

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

        mojo.apis = new ApiProxy[2];
        mojo.apis[0] = new ApiProxy();
        mojo.apis[0].setApiName("test");
        mojo.apis[0].setProxyClass(TestProxy.class.getName());
        mojo.apis[0].setFromSignatureFile(new File("src/test/resources/test-proxy-signatures.txt"));
        Substitution[] substitutions = new Substitution[2];
        substitutions[0] = new Substitution(".+", "(.+)", "java.util.List", "$1List", false);
        substitutions[1] = new Substitution(".+", "(.+)", ".*?(\\w++)\\[\\]", "$1Array", true);
        mojo.apis[0].setSubstitutions(substitutions);
        // exclude name2, and int times
        mojo.apis[0].setExcludeConfigNames("name2");
        mojo.apis[0].setExcludeConfigTypes("int");
        mojo.apis[0].setNullableOptions(new String[] {"namesList"});

        List<ApiMethodAlias> aliases = new ArrayList<>();
        aliases.add(new ApiMethodAlias("get(.+)", "$1"));
        aliases.add(new ApiMethodAlias("set(.+)", "$1"));
        mojo.apis[1] = new ApiProxy();
        mojo.apis[1].setApiName("velocity");
        mojo.apis[1].setProxyClass(VelocityContext.class.getName());
        mojo.apis[1].setAliases(aliases);
        Substitution substitution = new Substitution(".*", "key", "java.lang.Object", "applicationKey", false);
        mojo.apis[1].setSubstitutions(new Substitution[] {substitution});
        mojo.apis[1].setExtraOptions(new ExtraOption[] {new ExtraOption("java.util.Map<String, String>", "extraMap")});

        mojo.extraOptions = new ExtraOption[1];
        mojo.extraOptions[0] = new ExtraOption("java.util.List<String>", "extraStrings");

        final FromJavadoc fromJavadoc = new FromJavadoc();
        fromJavadoc.setExcludePackages(JavadocApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES);
        fromJavadoc.setExcludeMethods("clone|Current|internal|icache");
        mojo.apis[1].setFromJavadoc(fromJavadoc);
        return mojo;
    }
    

}
