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
package org.apache.camel.main.uberjar;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.util.LinkedList;

import org.apache.camel.main.KameletMain;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;

/**
 * Main class to run Camel as an uber-jar packaged by camel-jbang
 */
public class UberJarMain extends KameletMain {

    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    private UberJarMain main;

    public static void main(String[] args) throws Exception {
        UberJarMain main = new UberJarMain();
        main.main = main;
        int code = main.run(args);
        if (code != 0) {
            System.exit(code);
        }
        // normal exit
    }

    @Override
    public void showOptionsHeader() {
        System.out.println("Apache Camel (UberJar) takes the following options");
        System.out.println();
    }

    @Override
    protected void addInitialOptions() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
        addOption(new ParameterOption("prop", "property", "Additional properties (override existing)", "property") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                if (arg.equals("-prop") || arg.equals("-property")) {
                    String k = StringHelper.before(parameter, "=");
                    String v = StringHelper.after(parameter, "=");
                    if (k != null && v != null) {
                        main.addArgumentProperty(k, v);
                    }
                }
            }
        });
        addOption(new ParameterOption(
                "properties", "properties",
                "Load properties file for route placeholders (ex. /path/to/file.properties",
                "properties") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                if (arg.equals("-properties") && parameter != null) {
                    String[] filesLocation = parameter.split(",");
                    StringBuilder locations = new StringBuilder();
                    for (String file : filesLocation) {
                        if (!file.startsWith("file:")) {
                            if (!file.startsWith("/")) {
                                file = FileSystems.getDefault().getPath("").toAbsolutePath() + File.separator + file;
                            }
                            file = "file://" + file;
                        }
                        locations.append(file).append(",");
                    }
                    // there may be existing properties
                    String loc = main.getInitialProperties().getProperty("camel.component.properties.location");
                    if (loc != null) {
                        loc = loc + "," + locations;
                    } else {
                        loc = locations.toString();
                    }
                    main.addInitialProperty("camel.component.properties.location", loc);
                }
            }
        });
    }

    @Override
    protected ClassLoader createApplicationContextClassLoader() {
        // use the classloader that loaded this class
        return this.getClass().getClassLoader();
    }

    @Override
    protected void doBuild() throws Exception {
        setAppName("Apache Camel (UberJar)");
        setDownload(false); // no need for download as all is included in uber-jar

        // load configuration file
        OrderedProperties prop = new OrderedProperties();
        File f = new File(RUN_SETTINGS_FILE);
        if (f.exists()) {
            prop.load(new FileInputStream(f));
        } else {
            InputStream is = UberJarMain.class.getClassLoader().getResourceAsStream("/" + RUN_SETTINGS_FILE);
            if (is != null) {
                prop.load(is);
            }
        }

        // setup embedded log4j logging
        String loggingLevel = prop.getProperty("loggingLevel", "info");
        RuntimeUtil.configureLog(loggingLevel);

        // setup configurations
        for (String key : prop.stringPropertyNames()) {
            if ("camel.main.routesReloadEnabled".equals(key)) {
                // skip reload as this is not possible in uber-jar mode
                continue;
            }
            if (key.startsWith("camel.")) {
                String value = prop.getProperty(key);
                addInitialProperty(key, value);
            }
        }

        super.doBuild();
    }

}
