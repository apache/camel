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

import static org.junit.Assert.assertTrue;

/**
 * Base class for Generator MOJO tests.
 */
public class AbstractGeneratorMojoTest {
    protected static final String OUT_DIR = "target/generated-test-sources/camelComponent";
    protected static final String PACKAGE_PATH = AbstractGeneratorMojo.OUT_PACKAGE.replaceAll("\\.", "/") + "/";

    protected void assertExists(File outFile) {
        assertTrue("Generated file not found " + outFile.getPath(), outFile.exists());
    }
}
