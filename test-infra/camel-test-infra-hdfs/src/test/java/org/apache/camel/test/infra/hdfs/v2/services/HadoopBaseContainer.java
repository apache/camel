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

package org.apache.camel.test.infra.hdfs.v2.services;

import org.apache.camel.test.infra.common.TestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;

abstract class HadoopBaseContainer<T extends GenericContainer<T>> extends GenericContainer<T> {
    private static final String FROM_IMAGE_NAME = "fedora:37";
    private static final String FROM_IMAGE_BUILDER_ARG = "FROMIMAGE_BUILDER";
    private static final String FROM_IMAGE_ARG = "FROMIMAGE";

    public HadoopBaseContainer(Network network, String name) {
        super(new ImageFromDockerfile("localhost/hadoop-2x:camel", false)
                .withFileFromClasspath(".",
                        "org/apache/camel/test/infra/hdfs/v2/services/")
                .withBuildArg(FROM_IMAGE_BUILDER_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME))
                .withBuildArg(FROM_IMAGE_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME)));

        withNetwork(network);

        withCreateContainerCmdModifier(
                createContainerCmd -> {
                    createContainerCmd.withHostName(name);
                    createContainerCmd.withName(name);
                });
    }

    abstract int getHttpPort();
}
