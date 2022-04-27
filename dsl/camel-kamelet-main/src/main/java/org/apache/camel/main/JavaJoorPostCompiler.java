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

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.IOHelper;

import java.io.FileOutputStream;

public class JavaJoorPostCompiler {

    // TODO: add option to turn this on|off, and configure disk location

    public static void initJavaJoorPostCompiler(CamelContext context) {
        Registry registry = context.getRegistry();

        registry.bind("JavaJoorPostCompiler", new ByteCodeCompilePostProcessor());
    }

    private static class ByteCodeCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance) throws Exception {
            if (byteCode != null) {
                FileOutputStream fos = new FileOutputStream("." + name + ".class", false);
                fos.write(byteCode);
                IOHelper.close(fos);
            }
        }
    }

}
