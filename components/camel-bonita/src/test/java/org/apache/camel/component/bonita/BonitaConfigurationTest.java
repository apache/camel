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

package org.apache.camel.component.bonita;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.bonita.util.BonitaOperation;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BonitaConfigurationTest extends CamelTestSupport {

    @Test
    public void testBonitaConfigurationDefaults() {
        BonitaEndpoint bonitaEndpoint =
                context.getEndpoint("bonita:startCase", BonitaEndpoint.class);
        BonitaConfiguration configuration = bonitaEndpoint.getConfiguration();

        assertEquals("localhost", configuration.getHostname());
        assertEquals("8080", configuration.getPort());
    }

    @Test
    public void testBonitaConfigurationCustom() {
        BonitaEndpoint bonitaEndpoint = context.getEndpoint(
                "bonita:startCase?hostname=host&port=port&processName=processName&username=username&password=password",
                BonitaEndpoint.class);
        BonitaConfiguration configuration = bonitaEndpoint.getConfiguration();

        assertEquals("host", configuration.getHostname());
        assertEquals("port", configuration.getPort());
        assertEquals("processName", configuration.getProcessName());
        assertEquals("username", configuration.getUsername());
        assertEquals("password", configuration.getPassword());
        assertEquals(0, configuration.getOperation().compareTo(BonitaOperation.startCase));
    }

    @Test(expected = ResolveEndpointFailedException.class)
    public void testBonitaConfigurationWrongOperation() {
        BonitaEndpoint bonitaEndpoint =
                context.getEndpoint("bonita:wrongOperation", BonitaEndpoint.class);
    }

}
