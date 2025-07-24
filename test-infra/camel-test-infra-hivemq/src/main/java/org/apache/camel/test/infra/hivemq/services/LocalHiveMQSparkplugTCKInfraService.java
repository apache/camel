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
package org.apache.camel.test.infra.hivemq.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = HiveMQInfraService.class,
              description = "MQTT Platform HiveMQ",
              serviceAlias = "hive-mq",
              serviceImplementationAlias = "sparkplug")
public class LocalHiveMQSparkplugTCKInfraService extends AbstractLocalHiveMQService<LocalHiveMQSparkplugTCKInfraService> {

    LocalHiveMQSparkplugTCKInfraService() {
        super(LocalPropertyResolver.getProperty(LocalHiveMQSparkplugTCKInfraService.class,
                HiveMQProperties.HIVEMQ_SPARKPLUG_CONTAINER));
    }

    @Override
    protected HiveMQContainer initContainer(String imageName) {
        String dockerfileResourcePath = LocalPropertyResolver.getProperty(LocalHiveMQSparkplugTCKInfraService.class,
                HiveMQProperties.HIVEMQ_RESOURCE_PATH);

        ImageFromDockerfile newImage
                = new ImageFromDockerfile(imageName, false).withFileFromClasspath(".", dockerfileResourcePath);
        String newImageName = newImage.get();

        class TestInfraHiveMQContainer extends HiveMQContainer {
            public TestInfraHiveMQContainer(boolean fixedPort) {
                super(DockerImageName.parse(newImageName)
                        .asCompatibleSubstituteFor("hivemq/hivemq-ce"));

                if (fixedPort) {
                    addFixedExposedPort(1883, 1883);
                } else {
                    addExposedPort(1883);
                }
            }
        }

        return new TestInfraHiveMQContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }
}
