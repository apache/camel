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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XmlSpringBeansLoadTest {

    @Test
    public void testLoadRoutesBuilderFromXml() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // load spring XML <beans> with embedded <camelContext>
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/dsl/xml/io/springBeans.xml");

            Assertions.assertDoesNotThrow(() -> {
                // should be able to parse the file and not fail (camel-jbang supports creating spring beans)
                PluginHelper.getRoutesLoader(context).loadRoutes(resource);
            });
        }
    }

}
