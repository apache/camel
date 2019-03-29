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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.junit.Test;

public class SpringPropertiesComponentTest extends BaseSpringPropertiesComponentTest {

    @Test
    public void testResolutionOfPlaceholdersOnFactoryBean() {
        final CamelContextFactoryBean factoryBean = applicationContext.getBean("&camel-1",
            CamelContextFactoryBean.class);

        assertEquals("{{autoStartup}}", factoryBean.getAutoStartup());

        final CamelContext context = applicationContext.getBean("camel-1", CamelContext.class);

        assertTrue(context.isAutoStartup());
    }

}
