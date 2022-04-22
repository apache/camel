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
import java.util.function.Consumer;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "image", description = "Create Docker and OCI container images")
public class Image implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(Image.class);

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = {"-f", "--from"}, description = "Base Image", defaultValue = "gcr.io/distroless/java:11")
    private String from;

    @CommandLine.Option(names = {"-j", "--jar"}, required = true, description = "Jar filename")
    private String jar;

    @CommandLine.Option(names = {"-t", "--tag"}, description = "Image tag")
    private String tag;

    @CommandLine.Option(names = {"--push"}, description = "Push to the registry")
    private boolean push;

    @CommandLine.Option(names = {"-r", "--registry"}, description = "Registry image reference")
    private String registry;
    @CommandLine.Option(names = {"-u", "--username"}, description = "Registry username")
    private String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Registry password")
    private String password;

    @Override
    public Integer call() throws Exception {
        File jarFile = Paths.get(jar).toFile();
        Jib.from(from)
                .addLayer(Arrays.asList(Paths.get(jar)), "/deployments/")
                .setWorkingDirectory(AbsoluteUnixPath.get("/deployments"))
                .setEntrypoint("java", "-jar", jarFile.getName())
                .containerize(push ? getRegistry() : getDockerImage());
        return 0;
    }

    private Containerizer getDockerImage() throws InvalidImageReferenceException {
        return Containerizer.to(DockerDaemonImage.named(tag)).addEventHandler(LogEvent.class, getEventConsumer());
    }

    private Containerizer getRegistry() throws InvalidImageReferenceException {
        return Containerizer.to(
                        RegistryImage.named(registry).addCredential(username, password))
                .addEventHandler(LogEvent.class, getEventConsumer());
    }

    private Consumer<LogEvent> getEventConsumer() {
        return event -> {
            switch (event.getLevel()) {
                case ERROR:
                    LOG.error(event.getMessage());
                    break;
                case WARN:
                    LOG.warn(event.getMessage());
                    break;
                case DEBUG:
                    LOG.debug(event.getMessage());
                    break;
                default:
                    LOG.info(event.getMessage());
                    break;
            }
        };
    }
}
