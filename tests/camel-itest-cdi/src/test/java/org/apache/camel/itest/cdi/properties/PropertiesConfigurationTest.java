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
package org.apache.camel.itest.cdi.properties;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.component.properties.CdiPropertiesComponent;
import org.apache.camel.cdi.internal.CamelExtension;
import org.apache.deltaspike.core.impl.scope.conversation.ConversationBeanHolder;
import org.apache.deltaspike.core.impl.scope.viewaccess.ViewAccessBeanHolder;
import org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verify if {@link CamelExtension} with custom properties.
 */
@RunWith(Arquillian.class)
public class PropertiesConfigurationTest {

    @Inject
    private CamelContext camelContext;

    @Test
    public void checkContext() throws Exception {
        assertNotNull(camelContext);

        assertEquals("value1", camelContext.resolvePropertyPlaceholders("{{property1}}"));
        assertEquals("value2", camelContext.resolvePropertyPlaceholders("{{property2}}"));
        assertEquals("value1_value2", camelContext.resolvePropertyPlaceholders("{{property1}}_{{property2}}"));
    }

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addPackage(CamelExtension.class.getPackage())
            .addPackage(CdiPropertiesComponent.class.getPackage())
            .addClass(Camel1Config.class)
            .addClass(Camel2Config.class)
            // add a bunch of deltaspike packages so we can find those cdi beans to make arquillian happy
            .addPackage(WindowBeanHolder.class.getPackage())
            .addPackage(ConversationBeanHolder.class.getPackage())
            .addPackage(ViewAccessBeanHolder.class.getPackage())
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
}
