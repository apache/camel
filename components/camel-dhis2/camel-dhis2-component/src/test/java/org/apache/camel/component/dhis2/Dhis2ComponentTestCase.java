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
package org.apache.camel.component.dhis2;

import org.apache.camel.RuntimeCamelException;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Dhis2ComponentTestCase {
    @Test
    public void testGetClientThrowsExceptionGivenDhis2ConfigurationWithDhis2ClientAndBaseApiUrl() {
        Dhis2Configuration dhis2Configuration = new Dhis2Configuration();
        dhis2Configuration.setBaseApiUrl("https://play.dhis2.org/40.2.2/api");
        dhis2Configuration
                .setClient(Dhis2ClientBuilder.newClient("https://play.dhis2.org/40.2.2/api", "admin", "district").build());

        Dhis2Component dhis2Component = new Dhis2Component();
        RuntimeCamelException runtimeCamelException
                = assertThrows(RuntimeCamelException.class, () -> dhis2Component.getClient(dhis2Configuration));
        assertEquals(
                "Bad DHIS2 endpoint configuration: client option is mutually exclusive to baseApiUrl, username, password, and personalAccessToken. Either set `client`, or `baseApiUrl` and `username` and `password`, or `baseApiUrl` and `personalAccessToken`",
                runtimeCamelException.getMessage());
    }
}
