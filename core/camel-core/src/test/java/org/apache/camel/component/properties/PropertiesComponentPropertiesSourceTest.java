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

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PropertiesSource;
import org.junit.Test;

public class PropertiesComponentPropertiesSourceTest extends ContextTestSupport {
    @Test
    public void testPropertiesSourceFromRegistry() {
        context.getRegistry().bind("my-ps-1", new PropertiesPropertiesSource("ps1", "my-key-1", "my-val-1"));
        context.getRegistry().bind("my-ps-2", new PropertiesPropertiesSource("ps2", "my-key-2", "my-val-2"));
        context.start();

        assertEquals("my-val-1", context.resolvePropertyPlaceholders("{{my-key-1}}"));
        assertEquals("my-val-2", context.resolvePropertyPlaceholders("{{my-key-2}}"));
    }

    private static final class PropertiesPropertiesSource extends Properties implements PropertiesSource {
        private final String name;

        public PropertiesPropertiesSource(String name, String... kv) {
            assert kv.length % 2 == 0;

            this.name = name;

            for (int i = 0; i < kv.length; i += 2) {
                super.setProperty(kv[i], kv[i + 1]);
            }
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
