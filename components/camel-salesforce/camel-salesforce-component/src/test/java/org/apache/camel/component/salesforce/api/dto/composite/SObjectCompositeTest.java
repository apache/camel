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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Account_IndustryEnum;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SObjectCompositeTest {

    @JsonPropertyOrder({ "account__c", "contactId__c" })
    public static class AccountContactJunction__c extends AbstractDescribedSObjectBase {

        public AccountContactJunction__c() {
            getAttributes().setType("AccountContactJunction__c");
        }

        private String account__c;

        private String contactId__c;

        @Override
        public SObjectDescription description() {
            return new SObjectDescription();
        }

        public String getAccount__c() {
            return account__c;
        }

        public String getContactId__c() {
            return contactId__c;
        }

        public void setAccount__c(final String account__c) {
            this.account__c = account__c;
        }

        public void setContactId__c(final String contactId__c) {
            this.contactId__c = contactId__c;
        }
    }

    @JsonPropertyOrder({ "Name", "BillingStreet", "BillingCity", "BillingState", "Industry" })
    public static class TestAccount extends Account {
        // just for property order
    }

    @JsonPropertyOrder({ "LastName", "Phone" })
    public static class TestContact extends Contact {
        // just for property order
    }

    private final SObjectComposite composite;

    public SObjectCompositeTest() {
        composite = new SObjectComposite("38.0", true);

        // first insert operation via an external id
        final Account updateAccount = new TestAccount();
        updateAccount.setName("Salesforce");
        updateAccount.setBillingStreet("Landmark @ 1 Market Street");
        updateAccount.setBillingCity("San Francisco");
        updateAccount.setBillingState("California");
        updateAccount.setIndustry(Account_IndustryEnum.TECHNOLOGY);
        composite.addUpdate("Account", "001xx000003DIpcAAG", updateAccount, "UpdatedAccount");

        final Contact newContact = new TestContact();
        newContact.setLastName("John Doe");
        newContact.setPhone("1234567890");
        composite.addCreate(newContact, "NewContact");

        final AccountContactJunction__c junction = new AccountContactJunction__c();
        junction.setAccount__c("001xx000003DIpcAAG");
        junction.setContactId__c("@{NewContact.id}");
        composite.addCreate(junction, "JunctionRecord");
    }

    @Test
    public void shouldSerializeToJson() throws IOException {

        final String expectedJson = IOUtils
                .toString(
                        SObjectCompositeTest.class.getResourceAsStream(
                                "/org/apache/camel/component/salesforce/api/dto/composite_request_example.json"),
                        StandardCharsets.UTF_8);

        final ObjectMapper mapper
                = JsonUtils.createObjectMapper().copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                        .configure(SerializationFeature.INDENT_OUTPUT, true);

        final String serialized = mapper.writerFor(SObjectComposite.class).writeValueAsString(composite);
        assertThat(serialized).as("Should serialize as expected by Salesforce").isEqualTo(expectedJson);
    }
}
