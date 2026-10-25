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
package org.apache.camel.component.docker;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.docker.consumer.DockerEventsConsumer;
import org.apache.camel.component.docker.consumer.DockerStatsConsumer;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.component.docker.producer.AsyncDockerProducer;
import org.apache.camel.component.docker.producer.DockerProducer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage Docker containers.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "docker", title = "Docker", syntax = "docker:operation",
             category = { Category.CLOUD, Category.CONTAINER }, lenientProperties = true,
             headersClass = DockerConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "events", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.model.Event"),
                                           @BodyType.When(option = "operation", value = "stats", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.model.Statistics") },
                                   description = "The object of docker-java for each item of the stream: an Event of the"
                                                 + " daemon with events, the Statistics of the container in"
                                                 + " CamelDockerContainerId with stats"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "imagecreate",
                                                         carriers = {
                                                                 Carrier.STREAM, Carrier.BYTES, Carrier.TEXT, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "imagebuild",
                                                         carriers = { Carrier.STREAM, Carrier.FILE }) },
                                  description = "Not used by most operations, which take their input from the options and"
                                                + " headers, such as the container in CamelDockerContainerId. imagecreate"
                                                + " reads the image as a stream; imagebuild needs an InputStream or a"
                                                + " java.io.File of the build context, and another body is logged as an"
                                                + " error"),
           producerOut = @BodyType(value = {
                   Carrier.POJO, Carrier.LIST, Carrier.TEXT, Carrier.STREAM, Carrier.NULL,
                   Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "auth", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.model.AuthResponse"),
                                           @BodyType.When(option = "operation", value = "info", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.model.Info"),
                                           @BodyType.When(option = "operation", value = "ping", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "version", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.model.Version"),
                                           @BodyType.When(option = "operation", value = "imagebuild",
                                                          carriers = { Carrier.TEXT, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "imagecreate", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.CreateImageResponse"),
                                           @BodyType.When(option = "operation", value = "imageinspect", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.InspectImageResponse"),
                                           @BodyType.When(option = "operation", value = "imagelist", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "imagepull", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.PullImageResultCallback"),
                                           @BodyType.When(option = "operation", value = "imagepush", carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "imageremove",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "imagesearch", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "imagetag",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerattach",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "containercommit",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "containercopyfile",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "containercreate",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.CreateContainerResponse"),
                                           @BodyType.When(option = "operation", value = "containerdiff",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "inspectcontainer",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.InspectContainerResponse"),
                                           @BodyType.When(option = "operation", value = "containerkill",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerlist",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "containerlog",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "containerpause",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerrestart",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerremove",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerstart",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerstop",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containertop", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.TopContainerResponse"),
                                           @BodyType.When(option = "operation", value = "containerunpause",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "containerwait",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.WaitContainerResultCallback"),
                                           @BodyType.When(option = "operation", value = "execcreate", carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.ExecCreateCmdResponse"),
                                           @BodyType.When(option = "operation", value = "execstart", carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "networkconnect",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "networkcreate",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.github.dockerjava.api.command.CreateNetworkResponse"),
                                           @BodyType.When(option = "operation", value = "networkremove",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "The response of docker-java for the operation, such as the Version of"
                                                 + " version and a List of Containers for containerlist; the operations"
                                                 + " without a response, such as ping and containerstart, keep the body."
                                                 + " The streaming operations (imagepull, imagepush, containerattach,"
                                                 + " containerlog, containerwait, execstart) give the docker-java callback"
                                                 + " once the stream is done, not its frames; imagebuild gives the image id"
                                                 + " of the last line of the output, null when it is not the"
                                                 + " Successfully built line"))
public class DockerEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private DockerConfiguration configuration;

    public DockerEndpoint() {
    }

    public DockerEndpoint(String uri, DockerComponent component, DockerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getUsername() != null) {
            return Map.of("username", configuration.getUsername());
        }
        return null;
    }

    @Override
    public Producer createProducer() throws Exception {
        DockerOperation operation = configuration.getOperation();

        if (operation != null && operation.canProduce()) {
            if (operation.isAsync()) {
                return new AsyncDockerProducer(this);
            } else {
                return new DockerProducer(this);
            }
        } else {
            throw new DockerException(operation + " is not a valid producer operation");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DockerOperation operation = configuration.getOperation();

        Consumer consumer;
        if (operation == DockerOperation.EVENTS) {
            consumer = new DockerEventsConsumer(this, processor);
        } else if (operation == DockerOperation.STATS) {
            consumer = new DockerStatsConsumer(this, processor);
        } else {
            throw new DockerException(operation + " is not a valid consumer operation");
        }
        configureConsumer(consumer);
        return consumer;
    }

    public DockerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

}
