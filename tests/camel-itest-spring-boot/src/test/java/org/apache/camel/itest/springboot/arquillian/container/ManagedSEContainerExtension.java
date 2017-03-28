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
package org.apache.camel.itest.springboot.arquillian.container;

import org.apache.camel.itest.springboot.arquillian.container.jmx.CustomJMXProtocol;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.core.spi.LoadableExtension;

public class ManagedSEContainerExtension implements LoadableExtension {

    @Override
    public void register(final ExtensionBuilder builder) {
        builder.service(DeployableContainer.class, ManagedSEDeployableContainer.class);
        builder.service(Protocol.class, CustomJMXProtocol.class);
        builder.service(AuxiliaryArchiveAppender.class, SEContainerAppender.class);
    }
}
