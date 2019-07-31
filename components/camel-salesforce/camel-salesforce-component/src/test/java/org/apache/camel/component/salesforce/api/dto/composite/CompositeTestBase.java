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

import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Account_IndustryEnum;
import org.apache.camel.component.salesforce.dto.generated.Contact;

abstract class CompositeTestBase {

    final Account simpleAccount = new Account();

    final Contact smith = new Contact();

    final Contact evans = new Contact();

    final Contact bond = new Contact();

    final Contact moneypenny = new Contact();

    final Account simpleAccount2 = new Account();

    CompositeTestBase() {
        simpleAccount.setName("SampleAccount");
        simpleAccount.setPhone("1234567890");
        simpleAccount.setWebsite("www.salesforce.com");
        simpleAccount.setNumberOfEmployees(100);
        simpleAccount.setIndustry(Account_IndustryEnum.BANKING);

        smith.setLastName("Smith");
        smith.setTitle("President");
        smith.setEmail("sample@salesforce.com");

        evans.setLastName("Evans");
        evans.setTitle("Vice President");
        evans.setEmail("sample@salesforce.com");

        bond.setLastName("Bond");
        bond.setTitle("Agent to the crown");
        bond.setEmail("sample@salesforce.com");

        moneypenny.setLastName("Moneypenny");
        moneypenny.setTitle("Secretary");
        moneypenny.setEmail("sample@salesforce.com");

        simpleAccount2.setName("SampleAccount2");
        simpleAccount2.setPhone("1234567890");
        simpleAccount2.setWebsite("www.salesforce2.com");
        simpleAccount2.setNumberOfEmployees(100);
        simpleAccount2.setIndustry(Account_IndustryEnum.BANKING);
    }
}
