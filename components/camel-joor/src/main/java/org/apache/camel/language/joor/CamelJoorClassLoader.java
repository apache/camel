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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CompileStrategy;

/**
 * ClassLoader using {@link org.apache.camel.spi.ClassResolver} from Camel
 */
public class CamelJoorClassLoader extends URLClassLoader {

    private final CamelContext camelContext;
    private final ClassLoader parent;

    public CamelJoorClassLoader(URLClassLoader parent, CamelContext camelContext) {
        super(parent.getURLs(), parent);
        this.parent = parent;
        this.camelContext = camelContext;
    }

    @Override
    public String getName() {
        return "CamelJoorClassLoader";
    }

    @Override
    public URL[] getURLs() {
        List<URL> answer = new ArrayList<>();
        // add compile work-dir as URL so jOOR is able to dynamic load classes that has been compiled
        CompileStrategy cs = camelContext.getCamelContextExtension().getContextPlugin(CompileStrategy.class);
        if (cs != null && cs.getWorkDir() != null) {
            try {
                File dir = new File(cs.getWorkDir());
                answer.add(dir.toURI().toURL());
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        answer.addAll(Arrays.asList(super.getURLs()));
        URL[] arr = answer.toArray(new URL[0]);
        return arr;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return doLoadClass(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return doLoadClass(name);
    }

    public Class<?> doLoadClass(String name) throws ClassNotFoundException {
        // first try JavaJoorClassLoader
        ClassLoader joorClassLoader = camelContext.getClassResolver().getClassLoader("JavaJoorClassLoader");
        if (joorClassLoader != null) {
            try {
                return joorClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        // then try all of them
        for (ClassLoader cl : camelContext.getClassResolver().getClassLoaders()) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        // and then parent last
        return parent.loadClass(name);
    }

}
