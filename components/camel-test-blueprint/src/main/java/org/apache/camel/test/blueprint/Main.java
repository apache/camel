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
package org.apache.camel.test.blueprint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainSupport;
import org.osgi.framework.BundleContext;

/**
 * A command line tool for booting up a CamelContext using an OSGi Blueprint XML file
 */
public class Main extends MainSupport {

    protected static Main instance;
    private BundleContext bundleContext;
    private String descriptors = "OSGI-INF/blueprint/*.xml";
    private CamelContext camelContext;
    private String bundleName = "MyBundle";
    private boolean includeSelfAsBundle;
    private String configAdminPid;
    private String configAdminFileName;

    // ClassLoader used to scan for bundles in CamelBlueprintHelper.createBundleContext()
    private ClassLoader loader;

    public Main() {

        addOption(new ParameterOption("ac", "applicationContext",
                "Sets the classpath based OSGi Blueprint", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDescriptors(parameter);
            }
        });

        addOption(new ParameterOption("fa", "fileApplicationContext",
                "Sets the filesystem based OSGi Blueprint", "fileApplicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDescriptors(parameter);
            }
        });
        
        addOption(new ParameterOption("pid", "configAdminPid", 
                 "Sets the ConfigAdmin persistentId", "configAdminPid") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setConfigAdminPid(parameter);
            }
        });
        
        addOption(new ParameterOption("pf", "configAdminFileName", 
                  "Sets the ConfigAdmin persistent file name", "configAdminFileName") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setConfigAdminFileName(parameter);
            }
        });

    }

    public static void main(String... args) throws Exception {
        Main main = new Main();
        main.run(args);
    }

    /**
     * Returns the currently executing main
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (bundleContext == null) {
            String descriptors = getDescriptors();
            if (descriptors == null) {
                throw new IllegalArgumentException("Descriptors must be provided, with the name of the blueprint XML file");
            }
            LOG.debug("Starting Blueprint XML file: " + descriptors);
            if (configAdminPid != null && configAdminFileName != null) {
                // pid/file is used to set INITIAL content of ConfigAdmin to be used when blueprint container is started
                bundleContext = createBundleContext(bundleName, new String[] {configAdminFileName, configAdminPid});
            } else {
                bundleContext = createBundleContext(bundleName);
            }
            Set<Long> eventHistory = new HashSet<>();

            camelContext = CamelBlueprintHelper.getOsgiService(bundleContext, CamelContext.class);
            if (camelContext == null) {
                throw new IllegalArgumentException("Cannot find CamelContext in blueprint XML file: " + descriptors);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop camel context
        if (camelContext != null) {
            camelContext.stop();
        }
        // and then stop blueprint
        LOG.debug("Stopping Blueprint XML file: " + descriptors);
        CamelBlueprintHelper.disposeBundleContext(bundleContext);
        // call completed to properly stop as we count down the waiting latch
        completed();
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (camelContext != null) {
            return camelContext.createProducerTemplate();
        } else {
            return null;
        }
    }

    protected BundleContext createBundleContext() throws Exception {
        return createBundleContext(getClass().getSimpleName());
    }

    protected BundleContext createBundleContext(String name, String[]... configAdminPidFiles) throws Exception {
        return createBundleContext(name, loader, configAdminPidFiles);
    }

    protected BundleContext createBundleContext(String name, ClassLoader loader, String[]... configAdminPidFiles) throws Exception {
        return CamelBlueprintHelper.createBundleContext(name, descriptors, isIncludeSelfAsBundle(),
                CamelBlueprintHelper.BUNDLE_FILTER, CamelBlueprintHelper.BUNDLE_VERSION, null,
                loader, configAdminPidFiles);
    }

    @Override
    protected Map<String, CamelContext> getCamelContextMap() {
        Map<String, CamelContext> map = new HashMap<String, CamelContext>(1);
        if (camelContext != null) {
            map.put(camelContext.getName(), camelContext);
        }
        return map;
    }

    public String getDescriptors() {
        return descriptors;
    }

    public void setDescriptors(String descriptors) {
        this.descriptors = descriptors;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public boolean isIncludeSelfAsBundle() {
        return includeSelfAsBundle;
    }

    public void setIncludeSelfAsBundle(boolean includeSelfAsBundle) {
        this.includeSelfAsBundle = includeSelfAsBundle;
    }

    public String getConfigAdminPid() {
        return configAdminPid;
    }

    public void setConfigAdminPid(String pid) {
        this.configAdminPid = pid;
    }

    public String getConfigAdminFileName() {
        return configAdminFileName;
    }

    public void setConfigAdminFileName(String fileName) {
        this.configAdminFileName = fileName;
    }

    public void setLoader(ClassLoader loader) {
        this.loader = loader;
    }

}
