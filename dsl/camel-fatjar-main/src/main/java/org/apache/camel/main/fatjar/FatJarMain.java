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
package org.apache.camel.main.fatjar;

import java.io.FileInputStream;

import org.apache.camel.main.KameletMain;
import org.apache.camel.util.OrderedProperties;

/**
 * Main class to run Camel as a fat-jar packaged by camel-jbang
 */
public class FatJarMain extends KameletMain {

    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    public static void main(String... args) throws Exception {
        FatJarMain main = new FatJarMain();
        int code = main.run(args);
        if (code != 0) {
            System.exit(code);
        }
        // normal exit
    }

    @Override
    protected void doBuild() throws Exception {
        setAppName("Apache Camel (FatJar)");
        setDownload(false); // no need for download as all is included in fat-jar

        // load configuration file
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(RUN_SETTINGS_FILE));

        // setup embedded log4j logging
        String loggingLevel = prop.getProperty("loggingLevel", "info");
        RuntimeUtil.configureLog(loggingLevel);

        // setup configurations
        for (String key : prop.stringPropertyNames()) {
            String value = prop.getProperty(key);
            if (key.startsWith("camel.")) {
                addInitialProperty(key, value);
            }
        }

        super.doBuild();
    }

}
