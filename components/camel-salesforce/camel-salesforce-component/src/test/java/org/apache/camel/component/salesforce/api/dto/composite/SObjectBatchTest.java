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
package org.apache.camel.component.salesforce.api.dto.composite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch.Method;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.XStreamUtils;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Account_IndustryEnum;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SObjectBatchTest {

    private final SObjectBatch batch;

    public SObjectBatchTest() {
        batch = new SObjectBatch("37.0");

        final Account account = new Account();
        account.setName("NewAccountName");
        account.setIndustry(Account_IndustryEnum.ENVIRONMENTAL);
        batch.addCreate(account);

        batch.addDelete("Account", "001D000000K0fXOIAZ");

        batch.addGet("Account", "001D000000K0fXOIAZ", "Name", "BillingPostalCode");

        batch.addGetByExternalId("Account", "EPK", "12345");

        batch.addGetRelated("Account", "001D000000K0fXOIAZ", "CreatedBy", "Name");

        batch.addLimits();

        final Account updates1 = new Account();
        updates1.setName("NewName");
        updates1.setAccountNumber("AC12345");
        batch.addUpdate("Account", "001D000000K0fXOIAZ", updates1);

        final Account updates2 = new Account();
        updates2.setName("NewName");
        batch.addUpdateByExternalId("Account", "EPK", "12345", updates2);

        final Account updates3 = new Account();
        updates3.setName("NewName");
        batch.addUpsertByExternalId("Account", "EPK", "12345", updates3);

        batch.addGeneric(Method.PATCH, "/some/url");

        batch.addQuery("SELECT Name FROM Account");

        batch.addQueryAll("SELECT Name FROM Account");

        batch.addSearch("FIND {joe}");
    }

    @Test
    public void shouldSerializeToJson() throws JsonProcessingException {
        final String json = "{" + "\"batchRequests\":[" + "{" + "\"method\":\"POST\"," + "\"url\":\"v37.0/sobjects/Account/\"," + "\"richInput\":{" + "\"attributes\":{"
                            + "\"referenceId\":null," + "\"type\":\"Account\"," + "\"url\":null" + "}," + "\"Industry\":\"Environmental\"," + "\"Name\":\"NewAccountName\"" + "}"
                            + "}," + "{" + "\"method\":\"DELETE\"," + "\"url\":\"v37.0/sobjects/Account/001D000000K0fXOIAZ\"" + "}," + "{" + "\"method\":\"GET\","
                            + "\"url\":\"v37.0/sobjects/Account/001D000000K0fXOIAZ?fields=Name,BillingPostalCode\"" + "}," + "{" + "\"method\":\"GET\","
                            + "\"url\":\"v37.0/sobjects/Account/EPK/12345\"" + "}," + "{" + "\"method\":\"GET\","
                            + "\"url\":\"v37.0/sobjects/Account/001D000000K0fXOIAZ/CreatedBy?fields=Name\"" + "}," + "{" + "\"method\":\"GET\"," + "\"url\":\"v37.0/limits/\""
                            + "}," + "{" + "\"method\":\"PATCH\"," + "\"url\":\"v37.0/sobjects/Account/001D000000K0fXOIAZ\"," + "\"richInput\":{" + "\"attributes\":{"
                            + "\"referenceId\":null," + "\"type\":\"Account\"," + "\"url\":null" + "}," + "\"AccountNumber\":\"AC12345\"," + "\"Name\":\"NewName\"" + "}" + "},"
                            + "{" + "\"method\":\"PATCH\"," + "\"url\":\"v37.0/sobjects/Account/EPK/12345\"," + "\"richInput\":{" + "\"attributes\":{" + "\"referenceId\":null,"
                            + "\"type\":\"Account\"," + "\"url\":null" + "}," + "\"Name\":\"NewName\"" + "}" + "}," + "{" + "\"method\":\"PATCH\","
                            + "\"url\":\"v37.0/sobjects/Account/EPK/12345\"," + "\"richInput\":{" + "\"attributes\":{" + "\"referenceId\":null," + "\"type\":\"Account\","
                            + "\"url\":null" + "}," + "\"Name\":\"NewName\"" + "}" + "}," + "{" + "\"method\":\"PATCH\"," + "\"url\":\"v37.0/some/url\"" + "}," + "{"
                            + "\"method\":\"GET\"," + "\"url\":\"v37.0/query/?q=SELECT Name FROM Account\"" + "}," + "{" + "\"method\":\"GET\","
                            + "\"url\":\"v37.0/queryAll/?q=SELECT Name FROM Account\"" + "}," + "{" + "\"method\":\"GET\"," + "\"url\":\"v37.0/search/?q=FIND {joe}\"" + "}" + "]"
                            + "}";
        final ObjectMapper mapper = JsonUtils.createObjectMapper();
        final String serialized = mapper.writerFor(SObjectBatch.class).writeValueAsString(batch);
        assertEquals("Should serialize as expected by Salesforce", json, serialized);
    }

    @Test
    public void shouldSerializeToXml() {
        final String xml = "<batch>"//
                           + "<batchRequests>"//
                           + "<batchRequest>"//
                           + "<method>POST</method>"//
                           + "<url>v37.0/sobjects/Account/</url>"//
                           + "<richInput>"//
                           + "<Account>"//
                           + "<Name>NewAccountName</Name>"//
                           + "<Industry>Environmental</Industry>"//
                           + "</Account>"//
                           + "</richInput>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>DELETE</method>"//
                           + "<url>v37.0/sobjects/Account/001D000000K0fXOIAZ</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/sobjects/Account/001D000000K0fXOIAZ?fields=Name,BillingPostalCode</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/sobjects/Account/EPK/12345</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/sobjects/Account/001D000000K0fXOIAZ/CreatedBy?fields=Name</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/limits/</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>PATCH</method>"//
                           + "<url>v37.0/sobjects/Account/001D000000K0fXOIAZ</url>"//
                           + "<richInput>"//
                           + "<Account>"//
                           + "<Name>NewName</Name>"//
                           + "<AccountNumber>AC12345</AccountNumber>"//
                           + "</Account>"//
                           + "</richInput>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>PATCH</method>"//
                           + "<url>v37.0/sobjects/Account/EPK/12345</url>"//
                           + "<richInput>"//
                           + "<Account>"//
                           + "<Name>NewName</Name>"//
                           + "</Account>"//
                           + "</richInput>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>PATCH</method>"//
                           + "<url>v37.0/sobjects/Account/EPK/12345</url>"//
                           + "<richInput>"//
                           + "<Account>"//
                           + "<Name>NewName</Name>"//
                           + "</Account>"//
                           + "</richInput>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>PATCH</method>"//
                           + "<url>v37.0/some/url</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/query/?q=SELECT Name FROM Account</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/queryAll/?q=SELECT Name FROM Account</url>"//
                           + "</batchRequest>"//
                           + "<batchRequest>"//
                           + "<method>GET</method>"//
                           + "<url>v37.0/search/?q=FIND {joe}</url>"//
                           + "</batchRequest>"//
                           + "</batchRequests>"//
                           + "</batch>";

        final Class<?>[] classes = new Class[batch.objectTypes().length + 1];
        classes[0] = SObjectBatch.class;
        System.arraycopy(batch.objectTypes(), 0, classes, 1, batch.objectTypes().length);
        final XStream xStream = XStreamUtils.createXStream(classes);

        final String serialized = xStream.toXML(batch);

        assertEquals("Should serialize as expected by Salesforce", xml, serialized);
    }
}
