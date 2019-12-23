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
package org.apache.camel.component.ahc;

import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AhcProduceClientConfigWithClientConfigUriParametersTest extends AhcProduceClientConfigTest {

    @Override
    @Test
    public void testAhcProduceClientConfig() throws Exception {
        super.testAhcProduceClientConfig();
        
        Endpoint ahcEndpoint = context.getEndpoint(getAhcEndpointUri());
        assertTrue(ahcEndpoint instanceof AhcEndpoint);
        
        assertEquals(1, ((AhcEndpoint) ahcEndpoint).getClientConfig().getMaxConnections());
        assertEquals(3, ((AhcEndpoint) ahcEndpoint).getClientConfig().getMaxRequestRetry());
        assertEquals(true, ((AhcEndpoint) ahcEndpoint).getClientConfig().isFollowRedirect());
    }

    @Override
    protected String getAhcEndpointUri() {
        return super.getAhcEndpointUri() + "&clientConfig.maxConnections=1";
    }
}
