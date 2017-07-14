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
package org.apache.camel.component.properties;

import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

/**
 * @version 
 */
public class PropertiesComponentRestartTest extends ContextTestSupport {

    private int resolvedCount;

    public void testPropertiesComponentCacheClearedOnStop() throws Exception {

        context.start();

        context.resolvePropertyPlaceholders("{{cool.end}}");
        context.resolvePropertyPlaceholders("{{cool.end}}");
        context.resolvePropertyPlaceholders("{{cool.end}}");
        assertEquals(1, resolvedCount); // one cache miss

        context.stop();
        context.start();

        context.resolvePropertyPlaceholders("{{cool.end}}");
        context.resolvePropertyPlaceholders("{{cool.end}}");
        context.resolvePropertyPlaceholders("{{cool.end}}");
        assertEquals(2, resolvedCount); // one more cache miss -- stop() cleared the cache
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final PropertiesComponent pc = new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties");
        pc.setPropertiesResolver(new PropertiesResolver() {
            public Properties resolveProperties(CamelContext context, boolean ignoreMissingLocation, List<PropertiesLocation> locations) throws Exception {
                resolvedCount++;
                return new DefaultPropertiesResolver(pc).resolveProperties(context, ignoreMissingLocation, locations);
            }
        });

        // put the properties component into the registry so that it survives restarts
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("properties", pc);

        return new DefaultCamelContext(registry);
    }

}
