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
package org.apache.camel.component.mina2;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class Mina2ComponentWithConfigurationTest extends CamelTestSupport {

    @Test
    public void testMinaComponentWithConfiguration() throws Exception {
        Mina2Component comp = context.getComponent("mina2", Mina2Component.class);

        Mina2Configuration cfg1 = new Mina2Configuration();
        cfg1.setHost("abc");
        cfg1.setPort(4455);
        cfg1.setProtocol("tcp");
        Mina2Configuration cfg2 = new Mina2Configuration();
        cfg2.setHost("abc");
        cfg2.setPort(4455);
        cfg2.setProtocol("udp");


        Mina2Endpoint e1 = (Mina2Endpoint) comp.createEndpoint(cfg1);
        Mina2Endpoint e2 = (Mina2Endpoint) comp.createEndpoint(cfg2);

        // should not be same
        assertNotSame(e1, e2);
        assertNotSame(e1.getConfiguration(), e2.getConfiguration());

        e2.getConfiguration().setPort(5566);

        assertEquals(false, e1.getConfiguration().isTextline());
        assertEquals(false, e2.getConfiguration().isTextline());
        assertEquals(4455, e1.getConfiguration().getPort());
        assertEquals(5566, e2.getConfiguration().getPort());
    }
}
