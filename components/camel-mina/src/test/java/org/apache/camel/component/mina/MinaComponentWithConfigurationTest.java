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
package org.apache.camel.component.mina;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class MinaComponentWithConfigurationTest extends CamelTestSupport {

    @Test
    public void testMinaComponentWithConfiguration() throws Exception {
        MinaComponent comp = context.getComponent("mina", MinaComponent.class);

        MinaConfiguration cfg = new MinaConfiguration();
        cfg.setTextline(true);

        comp.setConfiguration(cfg);
        assertSame(cfg, comp.getConfiguration());

        MinaEndpoint e1 = (MinaEndpoint) comp.createEndpoint("mina://tcp://localhost:4455");
        MinaEndpoint e2 = (MinaEndpoint) comp.createEndpoint("mina://tcp://localhost:5566?sync=false");

        // should not be same
        assertNotSame(e1, e2);
        assertNotSame(e1.getConfiguration(), e2.getConfiguration());

        e2.getConfiguration().setPort(5566);

        assertEquals(true, e1.getConfiguration().isSync());
        assertEquals(false, e2.getConfiguration().isSync());
        assertEquals(true, e1.getConfiguration().isTextline());
        assertEquals(true, e2.getConfiguration().isTextline());
        assertEquals(4455, e1.getConfiguration().getPort());
        assertEquals(5566, e2.getConfiguration().getPort());
    }
}
