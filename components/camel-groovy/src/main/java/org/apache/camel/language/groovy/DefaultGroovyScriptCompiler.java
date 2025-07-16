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
import java.util.Collection;
import java.util.List;

import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Ordered;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.GroovyScriptCompiler;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.language.groovy.GroovyLanguage.RELOAD_ORDER;

@JdkService(GroovyScriptCompiler.FACTORY)
@ManagedResource(description = "Managed GroovyScriptCompiler")
public class DefaultGroovyScriptCompiler extends ServiceSupport
        implements CamelContextAware, GroovyScriptCompiler, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultGroovyScriptCompiler.class);

    private GroovyScriptClassLoader classLoader;
    private CamelContext camelContext;
    private EventNotifier notifier;
    private String scriptPattern;
    private String workDir;
    private long taken;
    private int counter;
    private boolean reload;

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

    @ManagedAttribute(description = "Number of times Groovy compiler has executed")
    public int getCompileSize() {
        return counter;
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

    @ManagedAttribute(description = "Directory for storing compiled Groovy sources as class files")
    public String getWorkDir() {
        return workDir;
    }

    @ManagedAttribute(description = "Whether re-compiling is enabled")
    public boolean isRecompileEnabled() {
        return reload;
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
                // make classloader available for groovy language
                context.getCamelContextExtension().addContextPlugin(GroovyScriptClassLoader.class, classLoader);
            }

            String profile = context.getCamelContextExtension().getProfile();
            if ("dev".equals(profile)) {
                reload = true;
                if (notifier == null) {
                    notifier = new ReloadNotifier();
                    getCamelContext().getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (scriptPattern != null) {
            LOG.info("Pre-compiling Groovy sources from: {}", scriptPattern);
            doCompile(scanForGroovySources(scriptPattern));
        }
    }

    protected Collection<Resource> scanForGroovySources(String scriptPattern) throws Exception {
        List<Resource> list = new ArrayList<>();

        PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
        for (String pattern : scriptPattern.split(",")) {
            for (Resource resource : resolver.findResources(pattern)) {
                if (resource.exists()) {
                    if ("classpath".equals(resource.getScheme()) || "file".equals(resource.getScheme())) {
                        list.add(resource);
                    }
                }
            }
        }

        return list;
    }

    protected void doCompile(Collection<Resource> resources) throws Exception {
        List<String> cps = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (Resource resource : resources) {
            String loc = resource.getLocation();
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
            if (codes.isEmpty()) {
                // nothing to compile
                return;
            }

            StopWatch watch = new StopWatch();

            // use work dir for writing compiled classes
            CompileStrategy cs = camelContext.getCamelContextExtension().getContextPlugin(CompileStrategy.class);
            if (cs != null && cs.getWorkDir() != null) {
                workDir = cs.getWorkDir();
            }

            // setup compiler via groovy shell
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.setClasspathList(cps);
            if (reload) {
                cc.setRecompileGroovySource(true);
            }
            if (workDir != null) {
                LOG.debug("Writing compiled Groovy classes to directory: {}", workDir);
                cc.setTargetDirectory(workDir);
            }

            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl == null) {
                cl = GroovyShell.class.getClassLoader();
            }
            GroovyShell shell = new GroovyShell(cl, cc);

            // parse code into classes and add to classloader
            for (String code : codes) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Compiling Groovy source:\n{}", code);
                }
                counter++;
                Class<?> clazz = shell.getClassLoader().parseClass(code);
                if (clazz != null) {
                    String name = clazz.getName();
                    LOG.debug("Compiled Groovy class: {}", name);
                    // remove before adding in case it's recompiled
                    classLoader.removeClass(name);
                    classLoader.addClass(name, clazz);
                }
            }
            taken += watch.taken();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (counter > 0) {
            LOG.debug("Compiled {} Groovy sources in {} millis", counter, taken);
        }

        IOHelper.close(classLoader);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (notifier != null) {
            getCamelContext().getManagementStrategy().removeEventNotifier(notifier);
            notifier = null;
        }
    }

    private final class ReloadNotifier extends SimpleEventNotifierSupport implements Ordered {

        @Override
        public void notify(CamelEvent event) throws Exception {
            // if context or route is reloading then clear classloader to ensure old scripts are removed from memory.
            if (event instanceof CamelEvent.CamelContextReloadingEvent || event instanceof CamelEvent.RouteReloadedEvent) {
                if (scriptPattern != null) {
                    // trigger re-compilation
                    if (classLoader != null) {
                        classLoader.clear();
                    }
                    LOG.info("Re-compiling Groovy sources from: {}", scriptPattern);
                    doCompile(scanForGroovySources(scriptPattern));
                }
            }
        }

        @Override
        public int getOrder() {
            // ensure this is triggered before groovy language reloader (we want first)
            return RELOAD_ORDER - 100;
        }
    }

}
