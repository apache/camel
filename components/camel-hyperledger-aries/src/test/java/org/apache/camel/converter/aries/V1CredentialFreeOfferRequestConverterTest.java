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
import org.hyperledger.aries.api.credentials.CredentialAttributes;
import org.hyperledger.aries.api.credentials.CredentialPreview;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialFreeOfferRequest;
import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class V1CredentialFreeOfferRequestConverterTest {

    static final Gson GSON = GsonConfig.defaultConfig();

    @Test
    public void testTypeConcertion() throws Exception {

        V1CredentialFreeOfferRequest reqObj = V1CredentialFreeOfferRequest.builder()
                .credDefId("ZELMbHHhBTEp2FQjDYr25:3:CL:4395:default")
                .credentialPreview(new CredentialPreview(
                        CredentialAttributes.from(Map.of(
                                "first_name", "Alice",
                                "last_name", "Garcia",
                                "ssn", "123-45-6789",
                                "degree", "Bachelor of Science, Marketing",
                                "status", "graduated",
                                "year", "2015",
                                "average", "5"))))
                .build();

        String json = GSON.toJson(reqObj);

        V1CredentialFreeOfferRequest resObj = V1CredentialFreeOfferRequestConverter.toAries(json);
        Assertions.assertEquals(reqObj, resObj);

        Map<String, Object> reqMap = Map.of(
                "cred_def_id", "ZELMbHHhBTEp2FQjDYr25:3:CL:4395:default",
                "credential_preview", Map.of("attributes", CredentialAttributes.from(Map.of(
                        "first_name", "Alice",
                        "last_name", "Garcia",
                        "ssn", "123-45-6789",
                        "degree", "Bachelor of Science, Marketing",
                        "status", "graduated",
                        "year", "2015",
                        "average", "5"))));

        resObj = V1CredentialFreeOfferRequestConverter.toAries(reqMap);
        Assertions.assertEquals(reqObj, resObj);
    }
}
