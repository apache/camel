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
package org.apache.camel.component.ignite;

import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.apache.camel.test.infra.ignite.services.IgniteService;
import org.apache.camel.test.infra.ignite.services.IgniteServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.ignite.Ignite;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractIgniteTest extends CamelTestSupport {

    @RegisterExtension
    public static IgniteService igniteService = IgniteServiceFactory.createService();

    @RegisterExtension
    public static TestEntityNameGenerator nameGenerator = new TestEntityNameGenerator();

    /**
     * A unique identifier for the ignite resource (cache, queue, set...) being tested.
     */
    protected String resourceUid;

    private Ignite ignite;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent(getScheme(), createComponent());
        return context;
    }

    protected IgniteConfiguration createConfiguration() {
        return igniteService.createConfiguration();
    }

    protected abstract String getScheme();

    protected abstract AbstractIgniteComponent createComponent();

    protected Ignite ignite() {
        if (ignite == null) {
            ignite = context.getComponent(getScheme(), AbstractIgniteComponent.class).getIgnite();
        }
        return ignite;
    }

    @BeforeEach
    void updateUid() {
        resourceUid = nameGenerator.getName() + UUID.randomUUID();
    }
}
