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
package org.apache.camel.language.joor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaJoorClassLoader extends ClassLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JavaJoorClassLoader.class);
    private final Map<String, Class<?>> classes = new HashMap<>();
    private String compileDirectory;

    public JavaJoorClassLoader() {
        super(JavaJoorClassLoader.class.getClassLoader());
    }

    public String getCompileDirectory() {
        return compileDirectory;
    }

    public void setCompileDirectory(String compileDirectory) {
        this.compileDirectory = compileDirectory;
    }

    @Override
    public String getName() {
        return "JavaJoorClassLoader";
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = classes.get(name);
        if (clazz != null) {
            return clazz;
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> clazz = classes.get(name);
        if (clazz != null) {
            return clazz;
        }
        throw new ClassNotFoundException(name);
    }

    public void addClass(String name, Class<?> clazz, byte[] code) {
        if (name != null && clazz != null) {
            classes.put(name, clazz);
        }
        if (name != null && code != null && compileDirectory != null) {
            saveByteCodeToDisk(compileDirectory, name, code);
        }
    }

    private static void saveByteCodeToDisk(String outputDirectory, String name, byte[] byteCode) {
        // write to disk (can be triggered multiple times so only write once)
        String fname = name.replace('.', '/');
        fname = outputDirectory + "/" + fname + ".class";
        File target = new File(fname);
        if (!target.exists()) {
            // create work-dir if needed
            String dir = FileUtil.onlyPath(fname);
            new File(dir).mkdirs();
            try {
                FileOutputStream fos = new FileOutputStream(target);
                LOG.debug("Writing compiled class: {} as bytecode to file: {}", name, target);
                fos.write(byteCode);
                IOHelper.close(fos);
            } catch (Exception e) {
                LOG.warn("Error writing compiled class: {} as bytecode to file: {} due to {}. This exception is ignored.",
                        name, target, e.getMessage());
            }
        }
    }

}
