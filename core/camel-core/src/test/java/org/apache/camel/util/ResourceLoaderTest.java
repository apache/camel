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
package org.apache.camel.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultResourceLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.ResourceResolverSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.propertiesOf;
import static org.apache.camel.util.FileUtil.copyFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceLoaderTest extends TestSupport {

    @Test
    public void testLoadFile() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource
                = PluginHelper.getResourceLoader(context).resolveResource("file:src/test/resources/log4j2.properties");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertTrue(text.contains("rootLogger"));
        }
    }

    @Test
    public void testLoadFileWithSpace() throws Exception {
        testDirectory("target/data/my space", true);
        copyFile(new File("src/test/resources/log4j2.properties"), testFile("target/data/my space/log4j2.properties").toFile());

        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                "file:" + testDirectory().toString() + "/target/data/my%20space/log4j2.properties");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertTrue(text.contains("rootLogger"));
        }
    }

    @Test
    public void testLoadClasspath() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("classpath:log4j2.properties");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertTrue(text.contains("rootLogger"));
        }
    }

    @Test
    public void testLoadClasspathDefault() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource("log4j2.properties");

            // need to be started as it triggers the fallback
            // resolver
            context.start();

            try (InputStream is = resource.getInputStream()) {
                assertNotNull(is);

                String text = context.getTypeConverter().convertTo(String.class, is);
                assertNotNull(text);
                assertTrue(text.contains("rootLogger"));
            }
        }
    }

    @Test
    public void testLoadFallback() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultResourceLoader loader = new DefaultResourceLoader();
            loader.setFallbackResolver(new ResourceResolverSupport("custom") {
                @Override
                public Resource resolve(String location) {
                    return ResourceHelper.fromString("custom", "fallback");
                }

                @Override
                protected Resource createResource(String location, String remaining) {
                    throw new UnsupportedOperationException();
                }
            });

            context.getCamelContextExtension().addContextPlugin(ResourceLoader.class, loader);

            Resource resource = PluginHelper.getResourceLoader(context).resolveResource("log4j2.properties");

            // need to be started as it triggers the fallback
            // resolver
            context.start();

            try (InputStream is = resource.getInputStream()) {
                assertNotNull(is);

                String text = context.getTypeConverter().convertTo(String.class, is);
                assertNotNull(text);
                assertEquals("fallback", text);
            }
        }
    }

    @Test
    public void testLoadRegistry() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind("myBean", "This is a log4j logging configuration file");

        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("ref:myBean");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertTrue(text.contains("log4j"));
        }
    }

    @Test
    public void testLoadBeanDoubleColon() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind("myBean",
                new AtomicReference<InputStream>(new ByteArrayInputStream("a".getBytes())));

        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("bean:myBean::get");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertEquals("a", text);
        }
    }

    @Test
    public void testLoadBeanDoubleColonLong() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind("my.company.MyClass",
                new AtomicReference<InputStream>(new ByteArrayInputStream("a".getBytes())));

        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("bean:my.company.MyClass::get");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertEquals("a", text);
        }
    }

    @Test
    public void testLoadBeanDot() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind("myBean",
                new AtomicReference<InputStream>(new ByteArrayInputStream("a".getBytes())));

        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("bean:myBean.get");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String text = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(text);
            assertEquals("a", text);
        }
    }

    @Test
    public void testLoadFileNotFound() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("file:src/test/resources/notfound.txt");

        assertFalse(resource.exists());
    }

    @Test
    public void testLoadClasspathNotFound() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("classpath:notfound.txt");

        assertFalse(resource.exists());
    }

    @Test
    public void testLoadFileAsUrl() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource
                = PluginHelper.getResourceLoader(context).resolveResource("file:src/test/resources/log4j2.properties");

        URL url = resource.getURI().toURL();
        assertNotNull(url);

        String text = context.getTypeConverter().convertTo(String.class, url);
        assertNotNull(text);
        assertTrue(text.contains("rootLogger"));

        context.stop();
    }

    @Test
    public void testLoadClasspathAsUrl() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("classpath:log4j2.properties");

        URL url = resource.getURI().toURL();
        assertNotNull(url);

        String text = context.getTypeConverter().convertTo(String.class, url);
        assertNotNull(text);
        assertTrue(text.contains("rootLogger"));

        context.stop();
    }

    @Test
    public void testLoadMem() throws Exception {
        final String raw = "to-be-encoded";

        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("mem:" + raw);

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String content = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(content);
            assertEquals(raw, content);
        }
    }

    @Test
    public void testLoadBase64() throws Exception {
        final String raw = "to-be-encoded";
        final String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("base64:" + encoded);

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String content = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(content);
            assertEquals(raw, content);
        }
    }

    @Test
    public void testLoadBase64WithPlaceholders() throws Exception {
        final String raw = "to-be-encoded";
        final String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        DefaultCamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(propertiesOf("my.encoded", encoded));
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("base64:{{my.encoded}}");

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String content = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(content);
            assertEquals(raw, content);
        }
    }

    @Test
    public void testLoadGzip() throws Exception {
        final String raw = "to-be-encoded";
        final String encoded;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(raw.getBytes(StandardCharsets.UTF_8));
            }

            encoded = Base64.getEncoder().encodeToString(out.toByteArray());
        }

        DefaultCamelContext context = new DefaultCamelContext();
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource("gzip:" + encoded);

        try (InputStream is = resource.getInputStream()) {
            assertNotNull(is);

            String content = context.getTypeConverter().convertTo(String.class, is);
            assertNotNull(content);
            assertEquals(raw, content);
        }
    }
}
