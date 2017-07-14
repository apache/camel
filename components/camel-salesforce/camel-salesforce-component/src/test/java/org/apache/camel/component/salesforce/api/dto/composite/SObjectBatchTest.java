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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import org.apache.camel.component.salesforce.api.dto.AnnotationFieldKeySorter;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch.Method;
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

        Account updates1 = new Account();
        updates1.setName("NewName");
        updates1.setAccountNumber("AC12345");
        batch.addUpdate("Account", "001D000000K0fXOIAZ", updates1);

        Account updates2 = new Account();
        updates2.setName("NewName");
        batch.addUpdateByExternalId("Account", "EPK", "12345", updates2);

        Account updates3 = new Account();
        updates3.setName("NewName");
        batch.addUpsertByExternalId("Account", "EPK", "12345", updates3);

        batch.addGeneric(Method.PATCH, "/some/url");

        batch.addQuery("SELECT Name FROM Account");

        batch.addQueryAll("SELECT Name FROM Account");

        batch.addSearch("FIND {joe}");
    }

    @Test
    public void shouldSerializeToJson() throws JsonProcessingException {
        final String json = Pattern.compile("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)", Pattern.DOTALL)
            .matcher("{"//
                + "\"batchRequests\" : ["//
                + "    {"//
                + "        \"method\" : \"POST\","//
                + "        \"url\" : \"v37.0/sobjects/Account/\","//
                + "        \"richInput\" : {\"Industry\" : \"Environmental\" , \"Name\" : \"NewAccountName\"}"//
                + "    },{"//
                + "        \"method\" : \"DELETE\","//
                + "        \"url\" : \"v37.0/sobjects/Account/001D000000K0fXOIAZ\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/sobjects/Account/001D000000K0fXOIAZ?fields=Name,BillingPostalCode\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/sobjects/Account/EPK/12345\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/sobjects/Account/001D000000K0fXOIAZ/CreatedBy?fields=Name\"},{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/limits/\""//
                + "    },{"//
                + "        \"method\" : \"PATCH\","//
                + "        \"url\" : \"v37.0/sobjects/Account/001D000000K0fXOIAZ\","//
                + "        \"richInput\" : {\"AccountNumber\" : \"AC12345\", \"Name\" : \"NewName\"}"//
                + "    },{"//
                + "        \"method\" : \"PATCH\","//
                + "        \"url\" : \"v37.0/sobjects/Account/EPK/12345\","//
                + "        \"richInput\" : {\"Name\" : \"NewName\"}"//
                + "    },{"//
                + "        \"method\" : \"PATCH\","//
                + "        \"url\" : \"v37.0/sobjects/Account/EPK/12345\","//
                + "        \"richInput\" : {\"Name\" : \"NewName\"}"//
                + "    },{"//
                + "        \"method\" : \"PATCH\","//
                + "        \"url\" : \"v37.0/some/url\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/query/?q=SELECT Name FROM Account\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/queryAll/?q=SELECT Name FROM Account\""//
                + "    },{"//
                + "        \"method\" : \"GET\","//
                + "        \"url\" : \"v37.0/search/?q=FIND {joe}\""//
                + "    }]"//
                + "}")
            .replaceAll("");

        final ObjectMapper mapper = new ObjectMapper();

        final String serialized = mapper.writerFor(SObjectBatch.class).writeValueAsString(batch);

        assertEquals("Should serialize as expected by Salesforce", json, serialized);
    }

    @Test
    public void shouldSerializeToXml() {
        final String xml = "<batch>\n"//
            + "  <batchRequests>\n"//
            + "    <batchRequest>\n"//
            + "      <method>POST</method>\n"//
            + "      <url>v37.0/sobjects/Account/</url>\n"//
            + "      <richInput>\n"//
            + "        <Account>\n"//
            + "          <Name>NewAccountName</Name>\n"//
            + "          <Industry>Environmental</Industry>\n"//
            + "        </Account>\n"//
            + "      </richInput>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>DELETE</method>\n"//
            + "      <url>v37.0/sobjects/Account/001D000000K0fXOIAZ</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/sobjects/Account/001D000000K0fXOIAZ?fields=Name,BillingPostalCode</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/sobjects/Account/EPK/12345</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/sobjects/Account/001D000000K0fXOIAZ/CreatedBy?fields=Name</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/limits/</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>PATCH</method>\n"//
            + "      <url>v37.0/sobjects/Account/001D000000K0fXOIAZ</url>\n"//
            + "      <richInput>\n"//
            + "        <Account>\n"//
            + "          <Name>NewName</Name>\n"//
            + "          <AccountNumber>AC12345</AccountNumber>\n"//
            + "        </Account>\n"//
            + "      </richInput>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>PATCH</method>\n"//
            + "      <url>v37.0/sobjects/Account/EPK/12345</url>\n"//
            + "      <richInput>\n"//
            + "        <Account>\n"//
            + "          <Name>NewName</Name>\n"//
            + "        </Account>\n"//
            + "      </richInput>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>PATCH</method>\n"//
            + "      <url>v37.0/sobjects/Account/EPK/12345</url>\n"//
            + "      <richInput>\n"//
            + "        <Account>\n"//
            + "          <Name>NewName</Name>\n"//
            + "        </Account>\n"//
            + "      </richInput>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>PATCH</method>\n"//
            + "      <url>v37.0/some/url</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/query/?q=SELECT Name FROM Account</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/queryAll/?q=SELECT Name FROM Account</url>\n"//
            + "    </batchRequest>\n"//
            + "    <batchRequest>\n"//
            + "      <method>GET</method>\n"//
            + "      <url>v37.0/search/?q=FIND {joe}</url>\n"//
            + "    </batchRequest>\n"//
            + "  </batchRequests>\n"//
            + "</batch>";

        final PureJavaReflectionProvider reflectionProvider = new PureJavaReflectionProvider(
            new FieldDictionary(new AnnotationFieldKeySorter()));
        final XStream xStream = new XStream(reflectionProvider);
        xStream.aliasSystemAttribute(null, "class");
        xStream.processAnnotations(SObjectBatch.class);
        xStream.processAnnotations(batch.objectTypes());

        final String serialized = xStream.toXML(batch);

        assertEquals("Should serialize as expected by Salesforce", xml, serialized);
    }
}
