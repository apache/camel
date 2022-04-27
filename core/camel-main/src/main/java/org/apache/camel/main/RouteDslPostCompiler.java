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
package org.apache.camel.main;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post compiler for java-joor-dsl that stores the compiled .java sources as .class files to disk.
 */
public class RouteDslPostCompiler implements CompilePostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RouteDslPostCompiler.class);

    private final String outputDirectory;

    public RouteDslPostCompiler(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
            throws Exception {
        if (byteCode != null) {
            // write to disk (can be triggered multiple times so only write once)
            File target = new File(outputDirectory + "/" + name + ".class");
            if (!target.exists()) {
                // create work-dir if needed
                new File(outputDirectory).mkdirs();
                FileOutputStream fos = new FileOutputStream(target);
                LOG.debug("Writing compiled class: {} as bytecode to file: {}", name, target);
                fos.write(byteCode);
                IOHelper.close(fos);
            }
        }
    }
}
