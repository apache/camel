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
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.util.IOHelper;

/**
 * Post compiler for java-joor-dsl that stores the compiled .java sources as .class files to disk.
 */
public class JavaJoorPostCompiler {

    public static void initJavaJoorPostCompiler(CamelContext context, String outputDirectory) {
        context.getRegistry().bind("JavaJoorDslPostCompiler", new ByteCodeCompilePostProcessor(outputDirectory));
    }

    private static class ByteCodeCompilePostProcessor implements CompilePostProcessor {

        private final String outputDirectory;

        public ByteCodeCompilePostProcessor(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (byteCode != null) {
                // create work-dir if needed
                new File(outputDirectory).mkdirs();
                // write to disk
                FileOutputStream fos = new FileOutputStream(outputDirectory + "/" + name + ".class", false);
                fos.write(byteCode);
                IOHelper.close(fos);
            }
        }
    }

}
