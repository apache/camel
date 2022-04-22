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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Callable;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "image", description = "Create Docker and OCI container images")
public class Image implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(Image.class);

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "-f", "--from" }, description = "Base Image", defaultValue = "gcr.io/distroless/java:11")
    private String from;

    @CommandLine.Option(names = { "-j", "--jar" }, required = true, description = "Jar filename")
    private String jar;

    @CommandLine.Option(names = { "-t", "--tag" }, required = true, description = "Image Tag")
    private String tag;

    @Override
    public Integer call() throws Exception {
        File jarFile = Paths.get(jar).toFile();
        Jib.from(from)
                .addLayer(Arrays.asList(Paths.get(jar)), "/deployments/")
                .setWorkingDirectory(AbsoluteUnixPath.get("/deployments"))
                .setEntrypoint("java", "-jar", jarFile.getName())
                .containerize(
                        Containerizer.to(DockerDaemonImage.named(tag))
                                .addEventHandler(LogEvent.class,
                                        logEvent -> LOG.info(logEvent.getLevel() + ": " + logEvent.getMessage())));
        return 0;
    }
}
