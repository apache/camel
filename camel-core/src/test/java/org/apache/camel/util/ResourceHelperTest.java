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
package org.apache.camel.util;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;

/**
 *
 */
public class ResourceHelperTest extends TestSupport {

    public void testLoadFile() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), "file:src/test/resources/log4j.properties");
        assertNotNull(is);

        String text = context.getTypeConverter().convertTo(String.class, is);
        assertNotNull(text);
        assertTrue(text.contains("log4j"));
        is.close();

        context.stop();
    }

    public void testLoadClasspath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), "classpath:log4j.properties");
        assertNotNull(is);

        String text = context.getTypeConverter().convertTo(String.class, is);
        assertNotNull(text);
        assertTrue(text.contains("log4j"));
        is.close();

        context.stop();
    }

    public void testLoadClasspathDefault() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), "log4j.properties");
        assertNotNull(is);

        String text = context.getTypeConverter().convertTo(String.class, is);
        assertNotNull(text);
        assertTrue(text.contains("log4j"));
        is.close();

        context.stop();
    }

    public void testLoadFileNotFound() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        try {
            ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), "file:src/test/resources/notfound.txt");
            fail("Should not find file");
        } catch (FileNotFoundException e) {
            assertEquals("src/test/resources/notfound.txt (No such file or directory)", e.getMessage());
        }

        context.stop();
    }

    public void testLoadClasspathNotFound() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        try {
            ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), "classpath:notfound.txt");
            fail("Should not find file");
        } catch (FileNotFoundException e) {
            assertEquals("Cannot find resource in classpath for URI: notfound.txt", e.getMessage());
        }

        context.stop();
    }

    public void testLoadFileAsUrl() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        URL url = ResourceHelper.resolveMandatoryResourceAsUrl(context.getClassResolver(), "file:src/test/resources/log4j.properties");
        assertNotNull(url);

        String text = context.getTypeConverter().convertTo(String.class, url);
        assertNotNull(text);
        assertTrue(text.contains("log4j"));

        context.stop();
    }

    public void testLoadClasspathAsUrl() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        URL url = ResourceHelper.resolveMandatoryResourceAsUrl(context.getClassResolver(), "classpath:log4j.properties");
        assertNotNull(url);

        String text = context.getTypeConverter().convertTo(String.class, url);
        assertNotNull(text);
        assertTrue(text.contains("log4j"));

        context.stop();
    }

}
