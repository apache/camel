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
package org.apache.camel.cdi;

import java.io.File;
import java.net.URL;

import org.apache.camel.model.RoutesDefinition;

/**
 * Tests loading of routes as XML from a URL
 */
public class XmlRoutesFromURLTest extends XmlRoutesFromClassPathTest {

    @Override
    public RoutesDefinition createRoutes() throws Exception {
        String[] prefixes = {"camel-cdi", "components"};
        String fileName = "src/test/resources/routes.xml";
        File file = new File(fileName);
        for (String prefix : prefixes) {
            if (file.exists()) {
                break;
            }
            file = new File(prefix, file.getPath());
        }
        assertTrue("The file " + file.getPath() + " does not exist", file.exists());
        URL url = file.toURI().toURL();
        return RoutesXml.loadRoutesFromURL(url);
    }
}
