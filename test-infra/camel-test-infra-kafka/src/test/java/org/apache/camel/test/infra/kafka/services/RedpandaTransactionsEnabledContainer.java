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
package org.apache.camel.test.infra.kafka.services;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.redpanda.RedpandaContainer;

public class RedpandaTransactionsEnabledContainer extends RedpandaContainer {

    public static final String DEFAULT_REDPANDA_CONTAINER = "docker.redpanda.com/vectorized/redpanda:v23.1.1";
    public static final String REDPANDA_CONTAINER
            = System.getProperty("itest.redpanda.container.image", DEFAULT_REDPANDA_CONTAINER);
    public static final int REDPANDA_PORT = 9092;

    public RedpandaTransactionsEnabledContainer(String image) {
        super(image);
    }

    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);
        String command = "#!/bin/bash\n";

        command += "/usr/bin/rpk redpanda start --mode dev-container ";
        command += "--kafka-addr PLAINTEXT://0.0.0.0:29092,OUTSIDE://0.0.0.0:9092 ";
        command += "--advertise-kafka-addr PLAINTEXT://kafka:29092,OUTSIDE://" + getHost() + ":" + getMappedPort(9092);

        this.copyFileToContainer(Transferable.of(command, 511), "/testcontainers_start.sh");
    }
}
