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
package org.apache.camel.component.osgi.activator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.container.internal.JavaVersionUtil;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.DefaultCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.tinybundles.core.TinyBundles;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
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
            logLevel(LogLevelOption.LogLevel.INFO),
            when(JavaVersionUtil.getMajorVersion() >= 9)
                .useOptions(
                        new VMOption("-classpath"),
                        new VMOption("lib/jdk9plus/*" + File.pathSeparator + "lib/boot/*")
                        )
        ),
        CAMEL_CORE_OSGI(
                createStreamBundleOption("camel-core-engine.jar"),
                createStreamBundleOption("camel-api.jar"),
                createStreamBundleOption("camel-base.jar"),
                createStreamBundleOption("camel-management-api.jar"),
                createStreamBundleOption("camel-support.jar"),
                createStreamBundleOption("camel-util.jar"),
                createStreamBundleOption("camel-timer.jar"),
                createStreamBundleOption("camel-log.jar"),
                createStreamBundleOption("camel-core-osgi.jar")
        );

    private final Option[] options;

    PaxExamOptions(Option... options) {
        this.options = options;
    }

    public Option option() {
        return new DefaultCompositeOption(options);
    }
    
    public static Option createStreamBundleOption(String fileName) {
        InputStream bundleInputStream = null;
        try {
            bundleInputStream = Files.newInputStream(
                    Paths.get("target/test-bundles")
                        .resolve(fileName));
            
        } catch (IOException e) {
            throw new RuntimeException("Error resolving Bundle", e);
        }
        
        return streamBundle(
                    TinyBundles.bundle()
                        .read(bundleInputStream)
                        .build());
    }
}
