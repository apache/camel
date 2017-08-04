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

import java.util.Arrays;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class CamelServiceNowGenerateMojoTest extends CamelServiceNowMojoTestSupport {

    @Test
    public void testExecute() throws Exception {
        final CamelServiceNowGenerateMojo mojo = createMojo();

        mojo.objects = Arrays.asList("incident");
        mojo.fields = Collections.singletonMap("incident", "sys_id");
        mojo.fieldsExcludePattern = Collections.singletonMap("incident", "^sys_.*$");

        mojo.execute();

        Assert.assertTrue("Output directory was not created", mojo.outputDirectory.exists());
        Assert.assertTrue("Output directory is empty", mojo.outputDirectory.list().length > 0);
    }
}
