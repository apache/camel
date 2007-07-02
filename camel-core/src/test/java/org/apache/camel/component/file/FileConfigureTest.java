/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;

import java.io.File;

/**
 * @version $Revision: 1.1 $
 */
public class FileConfigureTest extends ContextTestSupport {

    public void testUriConfigurations() throws Exception {
        assertFileEndpoint("file://target/foo/bar", "target/foo/bar");
        assertFileEndpoint("file://target/foo/bar?delete=true", "target/foo/bar");
        assertFileEndpoint("file:target/foo/bar?delete=true", "target/foo/bar");
        assertFileEndpoint("file:target/foo/bar", "target/foo/bar");
    }

    private void assertFileEndpoint(String endpointUri, String expectedPath) {
        FileEndpoint endpoint = resolveMandatoryEndpoint(endpointUri, FileEndpoint.class);
        assertNotNull("Could not find endpoint: " + endpointUri, endpoint);

        File file = endpoint.getFile();
        String path = file.getPath();
        assertEquals("For uri: " + endpointUri + " the file is not equal", expectedPath, path);
    }
}
