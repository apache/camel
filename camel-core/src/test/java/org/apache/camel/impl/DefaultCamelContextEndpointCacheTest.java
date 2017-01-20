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
package org.apache.camel.impl;

import java.util.Collection;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * @version 
 */
public class DefaultCamelContextEndpointCacheTest extends ContextTestSupport {

    public void testCacheEndpoints() throws Exception {
        // test that we cache at most 1000 endpoints in camel context to avoid it eating to much memory
        for (int i = 0; i < 1234; i++) {
            String uri = "my:endpoint?id=" + i;
            DefaultEndpoint e = new DefaultEndpoint() {
                public Producer createProducer() throws Exception {
                    return null;
                }
                public Consumer createConsumer(Processor processor) throws Exception {
                    return null;
                }
                public boolean isSingleton() {
                    return true;
                }
            };
            e.setCamelContext(context);
            e.setEndpointUri(uri);

            context.addEndpoint(uri, e);
        }

        // the eviction is async so force cleanup
        context.getEndpointRegistry().cleanUp();

        Collection<Endpoint> col = context.getEndpoints();
        assertEquals("Size should be 1000", 1000, col.size());
    }

}
