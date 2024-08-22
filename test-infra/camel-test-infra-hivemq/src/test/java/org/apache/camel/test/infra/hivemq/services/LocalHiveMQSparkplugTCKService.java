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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public final class LocalHiveMQSparkplugTCKService extends AbstractLocalHiveMQService<LocalHiveMQSparkplugTCKService> {

    LocalHiveMQSparkplugTCKService() {
        super(LocalPropertyResolver.getProperty(LocalHiveMQSparkplugTCKService.class,
                HiveMQProperties.HIVEMQ_SPARKPLUG_CONTAINER));
    }

    @Override
    protected HiveMQContainer initContainer(String imageName) {
        String hivemqContainerName
                = LocalPropertyResolver.getProperty(LocalHiveMQSparkplugTCKService.class, HiveMQProperties.HIVEMQ_CONTAINER);

        String dockerfileResourcePath = LocalPropertyResolver.getProperty(LocalHiveMQSparkplugTCKService.class,
                HiveMQProperties.HIVEMQ_RESOURCE_PATH);

        ImageFromDockerfile newImage
                = new ImageFromDockerfile(imageName, false).withFileFromClasspath(".", dockerfileResourcePath);
        String newImageName = newImage.get();

        HiveMQContainer newContainer
                = new HiveMQContainer(DockerImageName.parse(newImageName).asCompatibleSubstituteFor(hivemqContainerName));

        return newContainer;
    }
}
