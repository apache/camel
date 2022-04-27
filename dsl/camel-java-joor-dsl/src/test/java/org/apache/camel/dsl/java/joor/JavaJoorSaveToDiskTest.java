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
package org.apache.camel.dsl.java.joor;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavaJoorSaveToDiskTest {

    @Test
    public void testSaveToDisk() throws Exception {
        FileUtil.removeDir(new File("target/compiled"));

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            JavaRoutesBuilderLoader loader = new JavaRoutesBuilderLoader();
            loader.setCamelContext(context);
            loader.setCompileDirectory("target/compiled");
            loader.start();

            Resource resource = context.getResourceLoader().resolveResource("/routes/MyMockRoute.java");
            RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);

            builder.setCamelContext(context);
            builder.configure();
        }

        // should have saved to disk
        Assertions.assertTrue(new File("target/compiled/MyMockRoute.class").exists());

        // should be able to load from disk
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            JavaRoutesBuilderLoader loader = new JavaRoutesBuilderLoader();
            loader.setCamelContext(context);
            loader.setCompileDirectory("target/compiled");
            loader.setCompileLoadFirst(true);
            loader.start();

            Resource resource = context.getResourceLoader().resolveResource("/routes/MyMockRoute.java");
            RouteBuilder builder = (RouteBuilder) loader.loadRoutesBuilder(resource);

            builder.setCamelContext(context);
            builder.configure();
        }

    }

}
