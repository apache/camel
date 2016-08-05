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
package org.apache.camel.example.cdi.osgi;

import java.io.File;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.DefaultCompositeOption;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

public enum PaxExamOptions {

    KARAF(
        karafDistributionConfiguration()
            .frameworkUrl(
                maven()
                    .groupId("org.apache.karaf")
                    .artifactId("apache-karaf")
                    .versionAsInProject()
                    .type("zip"))
            .name("Apache Karaf")
            .useDeployFolder(false)
            .unpackDirectory(new File("target/paxexam/unpack/")),
        keepRuntimeFolder(),
        // Don't bother with local console output as it just ends up cluttering the logs
        configureConsole().ignoreLocalConsole(),
        // Force the log level to INFO so we have more details during the test. It defaults to WARN.
        logLevel(LogLevelOption.LogLevel.INFO)
    ),
    CAMEL_COMMANDS(
        mavenBundle()
            .groupId("org.apache.camel.karaf")
            .artifactId("camel-karaf-commands")
            .versionAsInProject(),
        mavenBundle()
            .groupId("org.apache.camel")
            .artifactId("camel-commands-core")
            .versionAsInProject(),
        mavenBundle()
            .groupId("org.apache.camel")
            .artifactId("camel-catalog")
            .versionAsInProject()
    ),
    PAX_CDI_IMPL(
        features(
            maven()
                .groupId("org.ops4j.pax.cdi")
                .artifactId("pax-cdi-features")
                .type("xml")
                .classifier("features")
                .versionAsInProject(),
            "pax-cdi-weld")
    ),
    CAMEL_CDI(
        features(
            maven()
                .groupId("org.apache.camel.karaf")
                .artifactId("apache-camel")
                .type("xml")
                .classifier("features")
                .versionAsInProject(),
            "camel-cdi")
    ),
    CAMEL_SJMS(
        mavenBundle()
            .groupId("org.apache.camel")
            .artifactId("camel-sjms")
            .versionAsInProject(),
        mavenBundle()
            .groupId("commons-pool")
            .artifactId("commons-pool")
            .versionAsInProject()
    ),
    ACTIVEMQ(
        features(
            maven()
                .groupId("org.apache.activemq")
                .artifactId("activemq-karaf")
                .type("xml")
                .classifier("features")
                .versionAsInProject(),
            "activemq-broker-noweb")
    );

    private final Option[] options;

    PaxExamOptions(Option... options) {
        this.options = options;
    }

    public Option option() {
        return new DefaultCompositeOption(options);
    }
}
