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

import java.util.Arrays;
import java.util.Map;

import com.google.gson.Gson;
import org.hyperledger.aries.api.schema.SchemaSendRequest;
import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SchemaSendRequestConverterTest {

    static final Gson GSON = GsonConfig.defaultConfig();

    @Test
    public void testTypeConcertion() throws Exception {

        SchemaSendRequest reqObj = SchemaSendRequest.builder()
                .schemaName("Transscript")
                .schemaVersion("1.2")
                .attributes(Arrays.asList("first_name", "last_name", "ssn", "degree", "status", "year", "average"))
                .build();

        String json = GSON.toJson(reqObj);

        SchemaSendRequest resObj = SchemaSendRequestConverter.toAries(json);
        Assertions.assertEquals(reqObj, resObj);

        Map<String, Object> reqMap = Map.of(
                "attributes", Arrays.asList("first_name", "last_name", "ssn", "degree", "status", "year", "average"),
                "schema_version", "1.2");

        resObj = SchemaSendRequestConverter.toAries(reqMap);
        Assertions.assertEquals(reqObj.getSchemaVersion(), resObj.getSchemaVersion());
        Assertions.assertEquals(reqObj.getAttributes(), resObj.getAttributes());
        Assertions.assertNull(resObj.getSchemaName());
    }
}
