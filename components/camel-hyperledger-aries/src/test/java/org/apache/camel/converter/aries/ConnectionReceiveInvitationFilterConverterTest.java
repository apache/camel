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
package org.apache.camel.converter.aries;

import java.util.Map;

import com.google.gson.Gson;
import org.hyperledger.aries.api.connection.ConnectionReceiveInvitationFilter;
import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConnectionReceiveInvitationFilterConverterTest {

    static final Gson GSON = GsonConfig.defaultConfig();

    @Test
    public void testTypeConcertion() throws Exception {

        ConnectionReceiveInvitationFilter obj = ConnectionReceiveInvitationFilterConverter.toAries(Map.of("auto_accept", true));
        Assertions.assertTrue(obj.getAutoAccept());

        obj = ConnectionReceiveInvitationFilterConverter.toAries(Map.of("auto_accept", "true", "foo", "bar"));
        Assertions.assertTrue(obj.getAutoAccept());

        obj = ConnectionReceiveInvitationFilterConverter.toAries(Map.of("foo", "bar"));
        Assertions.assertNull(obj);

        obj = ConnectionReceiveInvitationFilterConverter.toAries("{}");
        Assertions.assertNull(obj);

        obj = ConnectionReceiveInvitationFilterConverter.toAries("invalid");
        Assertions.assertNull(obj);
    }

    @Test
    public void testSnakeCase() throws Exception {

        String res = ConnectionReceiveInvitationFilterConverter.toSnakeCase("credentialDefinitionId");
        Assertions.assertEquals("credential_definition_id", res);
    }
}
