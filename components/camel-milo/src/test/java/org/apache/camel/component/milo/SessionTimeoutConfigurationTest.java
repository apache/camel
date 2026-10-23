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
package org.apache.camel.component.milo;

import org.apache.camel.component.milo.browse.MiloBrowseComponent;
import org.apache.camel.component.milo.browse.MiloBrowseEndpoint;
import org.apache.camel.component.milo.client.MiloClientComponent;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A session timeout configured on the component reaches its endpoints.
 */
public class SessionTimeoutConfigurationTest extends CamelTestSupport {

    private static final String SERVER = "opc.tcp://localhost:4840";

    @Test
    void clientComponentSetsTheDefaultSessionTimeoutOfItsEndpoints() {
        context.getComponent("milo-client", MiloClientComponent.class).getConfiguration().setSessionTimeout(30_000L);

        assertEquals(30_000L,
                context.getEndpoint("milo-client:" + SERVER, MiloClientEndpoint.class).getConfiguration()
                        .getSessionTimeout());
        assertEquals(45_000L,
                context.getEndpoint("milo-client:" + SERVER + "?sessionTimeout=45000", MiloClientEndpoint.class)
                        .getConfiguration().getSessionTimeout());
    }

    @Test
    void browseComponentSetsTheDefaultSessionTimeoutOfItsEndpoints() {
        context.getComponent("milo-browse", MiloBrowseComponent.class).getConfiguration().setSessionTimeout(30_000L);

        assertEquals(30_000L,
                context.getEndpoint("milo-browse:" + SERVER, MiloBrowseEndpoint.class).getConfiguration()
                        .getSessionTimeout());
        assertEquals(45_000L,
                context.getEndpoint("milo-browse:" + SERVER + "?sessionTimeout=45000", MiloBrowseEndpoint.class)
                        .getConfiguration().getSessionTimeout());
    }
}
