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

import java.util.ArrayList;
import java.util.List;

import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.GroovyScriptCompiler;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(GroovyScriptCompiler.FACTORY)
@ManagedResource(description = "Managed GroovyScriptCompiler")
public class DefaultGroovyScriptCompiler extends ServiceSupport
        implements CamelContextAware, GroovyScriptCompiler, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultGroovyScriptCompiler.class);

    private long taken;
    private GroovyScriptClassLoader classLoader;
    private CamelContext camelContext;
    private String scriptPattern;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @ManagedAttribute(description = "Total Groovy compilation time in millis")
    public long getCompileTime() {
        return taken;
    }

    @ManagedAttribute(description = "Number of Groovy sources that has been compiled")
    public int getClassesSize() {
        return classLoader.size();
    }

    @ManagedAttribute(description = "Directories to scan for Groovy source to be pre-compiled")
    @Override
    public String getScriptPattern() {
        return scriptPattern;
    }

    @Override
    public void setScriptPattern(String scriptPattern) {
        this.scriptPattern = scriptPattern;
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
    protected void doBuild() throws Exception {
        // register Groovy classloader to camel, so we are able to load classes we have compiled
        CamelContext context = getCamelContext();
        if (context != null) {
            // use existing class loader if available
            classLoader = (GroovyScriptClassLoader) context.getClassResolver().getClassLoader("GroovyScriptClassLoader");
            if (classLoader == null) {
                classLoader = new GroovyScriptClassLoader();
                context.getClassResolver().addClassLoader(classLoader);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (scriptPattern != null) {
            StopWatch watch = new StopWatch();

            LOG.info("Pre-compiling Groovy sources from: {}", scriptPattern);

            // make classloader available for groovy language
            camelContext.getCamelContextExtension().addContextPlugin(GroovyScriptClassLoader.class, classLoader);

            // scan for groovy source files to include
            List<String> cps = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
            for (String pattern : scriptPattern.split(",")) {
                for (Resource resource : resolver.findResources(pattern)) {
                    if (resource.exists()) {
                        String loc = null;
                        if ("classpath".equals(resource.getScheme())) {
                            loc = resource.getLocation();
                        } else if ("file".equals(resource.getScheme())) {
                            loc = resource.getLocation();
                        }
                        if (loc != null) {
                            String code = IOHelper.loadText(resource.getInputStream());
                            loc = StringHelper.after(loc, ":", loc);

                            String cp = FileUtil.onlyPath(loc);
                            if (cp == null) {
                                cp = ".";
                            }
                            if (!cps.contains(cp)) {
                                cps.add(cp);
                            }

                            codes.add(code);
                        }
                    }
                }
            }

            // setup compiler via groovy shell
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.setClasspathList(cps);

            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl == null) {
                cl = GroovyShell.class.getClassLoader();
            }
            GroovyShell shell = new GroovyShell(cl, cc);

            // parse code into classes and add to classloader
            for (String code : codes) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Pre-compiling Groovy source:\n{}", code);
                }
                Class<?> clazz = shell.getClassLoader().parseClass(code);
                if (clazz != null) {
                    LOG.debug("Pre-compiled Groovy class: {}", clazz.getName());
                    classLoader.addClass(clazz.getName(), clazz);
                }
            }
            taken += watch.taken();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (classLoader.size() > 0) {
            LOG.info("Pre-compiled {} Groovy sources in {} millis", classLoader.size(), taken);
        }

        IOHelper.close(classLoader);
    }
}
