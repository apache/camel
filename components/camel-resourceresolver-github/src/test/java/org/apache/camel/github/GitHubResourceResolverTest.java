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
package org.apache.camel.github;

import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitHubResourceResolverTest extends CamelTestSupport {

    @Test
    public void testGitHubLoadPom() throws Exception {
        Resource res = PluginHelper.getResourceLoader(context).resolveResource("github:apache:camel:main:core/pom.xml");
        assertNotNull(res);
        assertTrue(res.exists());

        String data = context.getTypeConverter().convertTo(String.class, res.getInputStream());
        assertNotNull(data);
        assertTrue(data.contains("<name>Camel :: Core Modules</name>"));
    }

    @Test
    public void testGitHubLoadMainDefault() throws Exception {
        Resource res = PluginHelper.getResourceLoader(context).resolveResource("github:apache:camel:core/pom.xml");
        assertNotNull(res);
        assertTrue(res.exists());

        String data = context.getTypeConverter().convertTo(String.class, res.getInputStream());
        assertNotNull(data);
        assertTrue(data.contains("<name>Camel :: Core Modules</name>"));
    }

    @Test
    public void testGitHubDoesNotExist() {
        Resource res = PluginHelper.getResourceLoader(context).resolveResource("github:apache:camel:main:core/unknown.xml");
        assertNotNull(res);
        assertFalse(res.exists());
    }
}
