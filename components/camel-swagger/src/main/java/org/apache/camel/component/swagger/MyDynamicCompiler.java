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
package org.apache.camel.component.swagger;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class MyDynamicCompiler {

    public static void main(String[] args) throws Exception{

        StringBuilder sb = new StringBuilder(64);
        sb.append("package foobar;\n");
        sb.append("public class HelloWorld {\n");
        sb.append("    public void doStuff() {\n");
        sb.append("        System.out.println(\"Hello world\");\n");
        sb.append("    }\n");
        sb.append("}\n");

        File file = new File("target/foobar/");
        file.mkdirs();
        file = new File("target/foobar/HelloWorld.java");
        Writer writer = new FileWriter("target/foobar/HelloWorld.java");
        writer.write(sb.toString());
        writer.flush();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

//        List<SimpleJavaFileObject> list = new ArrayList<SimpleJavaFileObject>();
//        list.add(new JavaSourceFromString("foobar.HelloWorld", sb.toString()));

        boolean done = compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjectsFromFiles(Arrays.asList(file))).call();
        System.out.println(done);

        URLClassLoader classLoader = new URLClassLoader(new URL[]{new File("target").toURI().toURL()});
        Class clazz = classLoader.loadClass("foobar.HelloWorld");
        Object obj = clazz.newInstance();

        System.out.println(obj);


        fileManager.close();
    }



    public static class JavaSourceFromString extends SimpleJavaFileObject {

        /**
         * The source code of this "file".
         */
        final String code;

        /**
         * Constructs a new JavaSourceFromString.
         * @param name the name of the compilation unit represented by this file object
         * @param code the source code for the compilation unit represented by this file object
         */
        public JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
