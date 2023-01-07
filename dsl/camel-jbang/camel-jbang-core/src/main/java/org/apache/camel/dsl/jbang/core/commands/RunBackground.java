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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.util.Properties;
import java.util.StringJoiner;

import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.util.CamelCaseOrderedProperties;
import picocli.CommandLine;

@CommandLine.Command(name = "run-background", description = "Run Camel integration in background")
public class RunBackground extends CamelCommand {

    public RunBackground(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        return run();
    }

    private int run() throws Exception {
        File source = new File(Run.WORK_DIR + "/" + Run.RUN_SETTINGS_FILE);
        Properties settings = loadProfileProperties(source);
        if (settings.isEmpty()) {
            System.err.println("Cannot load settings. Use camel run --background instead!");
            return 1;
        }

        Run run = new Run(getMain());
        return runBackground(run, settings);
    }

    private Properties loadProfileProperties(File source) throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, source);

        // special for routes include pattern that we need to "fix" after reading from properties
        // to make this work in run command
        String value = prop.getProperty("camel.main.routesIncludePattern");
        if (value != null) {
            // if not scheme then must use file: as this is what run command expects
            StringJoiner sj = new StringJoiner(",");
            for (String part : value.split(",")) {
                if (!part.contains(":")) {
                    part = "file:" + part;
                }
                sj.add(part);
            }
            value = sj.toString();
            prop.setProperty("camel.main.routesIncludePattern", value);
        }

        return prop;
    }

    protected Integer runBackground(Run cmd, Properties settings) throws Exception {
        cmd.profile = Run.WORK_DIR + "/camel-jbang-run";
        cmd.loggingLevel = settings.getProperty("loggingLevel", "INFO");
        cmd.loggingColor = "true".equals(settings.getProperty("loggingColor", "true"));
        cmd.loggingJson = "true".equals(settings.getProperty("loggingJson", "false"));
        cmd.loggingLevel = settings.getProperty("loggingLevel");
        cmd.name = settings.getProperty("camel.main.name", "CamelJBang");
        cmd.dev = "true".equals(settings.getProperty("camel.main.sourceLocationEnabled"));
        cmd.trace = "true".equals(settings.getProperty("camel.main.tracing"));
        cmd.modeline = "true".equals(settings.getProperty("camel.main.modeline"));
        cmd.openapi = settings.getProperty("camel.jbang.openApi");
        cmd.repos = settings.getProperty("camel.jbang.repos");
        cmd.health = "true".equals(settings.getProperty("camel.jbang.health"));
        cmd.console = "true".equals(settings.getProperty("camel.jbang.console"));
        String s = settings.getProperty("camel.main.durationMaxMessages");
        if (s != null) {
            cmd.maxMessages = Integer.parseInt(s);
        }
        s = settings.getProperty("camel.main.durationMaxSeconds");
        if (s != null) {
            cmd.maxSeconds = Integer.parseInt(s);
        }
        s = settings.getProperty("camel.main.durationMaxIdleSeconds");
        if (s != null) {
            cmd.maxIdleSeconds = Integer.parseInt(s);
        }
        s = settings.getProperty("camel.jbang.platform-http.port");
        if (s != null) {
            cmd.port = Integer.parseInt(s);
        }
        cmd.jfr = "jfr".equals(settings.getProperty("camel.jbang.jfr-profile"));
        cmd.jfrProfile = settings.getProperty("camel.jbang.jfr-profile");
        cmd.propertiesFiles = settings.getProperty("camel.component.properties.location");

        return cmd.runBackground();
    }

}
