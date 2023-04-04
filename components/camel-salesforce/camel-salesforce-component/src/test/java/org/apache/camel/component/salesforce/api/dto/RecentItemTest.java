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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RecentItemTest {

    @Test
    public void shouldDeserializeFromJSON() throws IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final Object read = mapper.readerFor(RecentItem.class).readValue("{ \n" + //
                                                                         "    \"attributes\" : \n" + //
                                                                         "    { \n" + //
                                                                         "        \"type\" : \"Account\", \n" + //
                                                                         "        \"url\" : \"/services/data/v28.0/sobjects/Account/a06U000000CelH0IAJ\" \n"
                                                                         + //
                                                                         "    }, \n" + //
                                                                         "    \"Id\" : \"a06U000000CelH0IAJ\", \n" + //
                                                                         "    \"Name\" : \"Acme\" \n" + //
                                                                         "}");

        assertThat("RecentItem should deserialize from JSON", read, instanceOf(RecentItem.class));

        final RecentItem recentItem = (RecentItem) read;

        assertEquals("a06U000000CelH0IAJ", recentItem.getId(), "RecentItem.Id should be deserialized");

        assertEquals("Acme", recentItem.getName(), "RecentItem.Name should be deserialized");

        assertNotNull(recentItem.getAttributes(), "RecentItem.attributes should be deserialized");

        assertEquals("Account", recentItem.getAttributes().getType(), "RecentItem.attributes.type should be deserialized");

        assertEquals("/services/data/v28.0/sobjects/Account/a06U000000CelH0IAJ", recentItem.getAttributes().getUrl(),
                "RecentItem.attributes.url should be deserialized");

    }
}
