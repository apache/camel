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
package org.apache.camel.component.jms.issues;

import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.camel.test.infra.artemis.services.ArtemisEmbeddedServiceBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;

@Tags({ @Tag("not-parallel"), @Tag("spring") })
public class CamelBrokerClientTestSupport extends CamelSpringTestSupport {

    @RegisterExtension
    public static ArtemisService service = new ArtemisEmbeddedServiceBuilder()
            .withCustomConfiguration(configuration -> {
                AddressSettings addressSettings = new AddressSettings();
                addressSettings.setMaxSizeMessages(5);
                configuration.addAddressSetting("#", addressSettings);
            })
            .build();

    public static String getServiceAddress() {
        return service.serviceAddress();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                new String[] {
                        "classpath:org/apache/camel/component/jms/issues/camelBrokerClient.xml" });
    }
}
