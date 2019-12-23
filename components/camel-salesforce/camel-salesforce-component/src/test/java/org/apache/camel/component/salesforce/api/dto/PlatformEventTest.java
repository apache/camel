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
package org.apache.camel.component.salesforce.api.dto;

import java.io.IOException;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class PlatformEventTest {

    @Test
    public void shouldDeserialize() throws IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final PlatformEvent platformEvent = mapper.readValue("{\n" + //
                                                             "  \"CreatedDate\": \"2017-04-14T13:35:23Z\", \n" + //
                                                             "  \"CreatedById\": \"005B00000031mqb\", \n" + //
                                                             "  \"Order_Number__c\": \"10013\", \n" + //
                                                             "  \"Type__c\": \"Placed\"\n" + //
                                                             "}", PlatformEvent.class);

        assertThat(platformEvent.getCreated()).isEqualTo(ZonedDateTime.parse("2017-04-14T13:35:23Z"));
        assertThat(platformEvent.getCreatedById()).isEqualTo("005B00000031mqb");
        assertThat(platformEvent.getEventData()).containsOnly(entry("Order_Number__c", "10013"), entry("Type__c", "Placed"));
    }
}
