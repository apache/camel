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
package org.apache.camel.component.salesforce.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.dto.LoginToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LoginTokenTest {

    @Test
    public void testLoginTokenWithUnknownFields() throws Exception {

        String salesforceOAuthResponse
                = """
                        {
                            "access_token": "00XXXXXXXXXXXX!ARMAQKg_lg_hGaRElvizVFBQHoCpvX8tzwGnROQ0_MDPXSceMeZHtm3JHkPmMhlgK0Km3rpJkwxwHInd_8o022KsDy.p4O.X",
                            "is_readonly": "false",
                            "signature": "XXXXXXXXXX+MYU+JrOXPSbpHa2ihMpSvUqow1iTPh7Q=",
                            "instance_url": "https://xxxxxxxx--xxxxxxx.cs5.my.salesforce.com",
                            "id": "https://test.salesforce.com/id/00DO00000054tO8MAI/005O0000001cmmdIAA",
                            "token_type": "Bearer",
                            "issued_at": "1442798068621",
                            "an_unrecognised_field": "foo"
                        }""";
        ObjectMapper mapper = JsonUtils.createObjectMapper();
        Exception e = null;
        LoginToken token = null;
        try {
            token = mapper.readValue(salesforceOAuthResponse, LoginToken.class);
        } catch (Exception ex) {
            e = ex;
        }

        // assert ObjectMapper deserialized the SF OAuth response and returned a
        // valid token back
        assertNotNull(token, "An invalid token was returned");
        // assert No exception was thrown during the JSON deserialization
        // process
        assertNull(e, "Exception was thrown during JSON deserialisation");
        // assert one of the token fields
        assertEquals("false", token.getIsReadOnly());

    }

}
