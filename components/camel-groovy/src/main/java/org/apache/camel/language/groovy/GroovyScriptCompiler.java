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
package org.apache.camel.language.groovy;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroovyScriptCompiler extends ServiceSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyScriptCompiler.class);

    private GroovyScriptClassLoader classLoader;
    private CamelContext camelContext;
    private String folder;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    /**
     * Loads the given class
     *
     * @param  name                   the FQN class name
     * @return                        the loaded class
     * @throws ClassNotFoundException is thrown if class is not found
     */
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (classLoader != null) {
            return classLoader.findClass(name);
        } else {
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (folder != null) {
            LOG.info("Pre compiling groovy scripts from: {}", folder);

            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl == null) {
                cl = GroovyShell.class.getClassLoader();
            }
            classLoader = new GroovyScriptClassLoader(cl);
            camelContext.getClassResolver().addClassLoader(classLoader);

            // make classloader available for groovy language
            camelContext.getCamelContextExtension().addContextPlugin(GroovyScriptClassLoader.class, classLoader);

            CompilerConfiguration cc = new CompilerConfiguration();
            cc.setClasspathList(List.of(folder));

            GroovyShell shell = new GroovyShell(cl, cc);

            // discover each class from the folder
            File[] files = new File(folder).listFiles();
            if (files != null) {
                for (File f : files) {
                    String code = IOHelper.loadText(new FileInputStream(f));
                    Class<?> clazz = shell.getClassLoader().parseClass(code);
                    if (clazz != null) {
                        classLoader.addClass(clazz.getName(), clazz);
                    }
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        IOHelper.close(classLoader);
    }
}
