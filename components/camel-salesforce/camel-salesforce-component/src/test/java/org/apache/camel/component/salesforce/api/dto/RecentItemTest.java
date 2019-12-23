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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class RecentItemTest {

    @Test
    public void shouldDeserializeFromJSON() throws JsonProcessingException, IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final Object read = mapper.readerFor(RecentItem.class).readValue("{ \n" + //
                                                                         "    \"attributes\" : \n" + //
                                                                         "    { \n" + //
                                                                         "        \"type\" : \"Account\", \n" + //
                                                                         "        \"url\" : \"/services/data/v28.0/sobjects/Account/a06U000000CelH0IAJ\" \n" + //
                                                                         "    }, \n" + //
                                                                         "    \"Id\" : \"a06U000000CelH0IAJ\", \n" + //
                                                                         "    \"Name\" : \"Acme\" \n" + //
                                                                         "}");

        assertThat("RecentItem should deserialize from JSON", read, instanceOf(RecentItem.class));

        final RecentItem recentItem = (RecentItem)read;

        assertEquals("RecentItem.Id should be deserialized", recentItem.getId(), "a06U000000CelH0IAJ");

        assertEquals("RecentItem.Name should be deserialized", recentItem.getName(), "Acme");

        assertNotNull("RecentItem.attributes should be deserialized", recentItem.getAttributes());

        assertEquals("RecentItem.attributes.type should be deserialized", recentItem.getAttributes().getType(), "Account");

        assertEquals("RecentItem.attributes.url should be deserialized", recentItem.getAttributes().getUrl(), "/services/data/v28.0/sobjects/Account/a06U000000CelH0IAJ");

    }
}
