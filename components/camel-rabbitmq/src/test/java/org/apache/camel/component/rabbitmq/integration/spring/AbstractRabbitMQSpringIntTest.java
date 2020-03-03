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
package org.apache.camel.component.rabbitmq.integration.spring;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.camel.test.testcontainers.Containers;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractRabbitMQSpringIntTest extends CamelSpringTestSupport {

    // Container starts once per test class
    protected static GenericContainer container;

    protected List<GenericContainer<?>> containers = new CopyOnWriteArrayList();

    protected abstract String getConfigLocation();

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        container = org.apache.camel.component.rabbitmq.integration.DockerTestUtils.rabbitMQContainer();
        this.containers.add(container);
        try {
            Containers.start(this.containers, null, 10);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext(getConfigLocation());
        return classPathXmlApplicationContext;
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();

        if (container != null) {
            container.stop();
        }
    }
}
