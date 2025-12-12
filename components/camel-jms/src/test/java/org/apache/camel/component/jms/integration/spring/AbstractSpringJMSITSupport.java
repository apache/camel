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
package org.apache.camel.component.jms.integration.spring;

import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Do not create unit tests using Spring: CamelSpringTestSupport does not provide a safe environment for concurrent
 * execution. This class should be used only for integration tests
 */
public abstract class AbstractSpringJMSITSupport extends CamelSpringTestSupport {

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    /**
     * Used by spring xml configurations
     *
     * @return the service address
     */
    public static String getServiceAddress() {
        return service.serviceAddress();
    }

    @Override
    protected abstract AbstractApplicationContext createApplicationContext();
}
