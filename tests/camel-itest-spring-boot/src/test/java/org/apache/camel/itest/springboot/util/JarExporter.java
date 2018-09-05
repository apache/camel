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
package org.apache.camel.itest.springboot.util;

import java.io.File;

import org.apache.camel.itest.springboot.ITestConfigBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.Test;

/**
 * Utility to export a spring-boot jar and check the content.
 */
public class JarExporter {

    @Test
    public void exportJar() throws Exception {

        Archive<?> archive = ArquillianPackager.springBootPackage(new ITestConfigBuilder()
                .module("camel-websocket")
                .build());

        new ZipExporterImpl(archive).exportTo(new File("target/export.zip"), true);

    }


}
