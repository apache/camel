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
package org.apache.camel.example.artemis.amqp;

import java.util.Dictionary;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class ArtemisAmqpIntTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return    "/OSGI-INF/blueprint/camel-context.xml,"

                //We add the embedded Artemis Broker
                + "/OSGI-INF/blueprint/embedded-broker.xml";
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) { 
        //obtain an available port
        int port = AvailablePortFinder.getNextAvailable();

        if (port != 9090) {
            //override the Netty port to use
            props.put("netty.port", "" + port);

            //return the PID of the config-admin we are using in the blueprint xml file
            return "my-placeholders";
        } else {
            // no update needed
            return null;
        }
    }

    @Test
    public void testEmbeddedBroker() throws Exception {
        //trigger
        String response = template.requestBody("netty-http:http://localhost:{{netty.port}}/message", null, String.class);

        //response validation
        assertEquals("not expected", "Hello from Camel's AMQP example", response);
    }
}
