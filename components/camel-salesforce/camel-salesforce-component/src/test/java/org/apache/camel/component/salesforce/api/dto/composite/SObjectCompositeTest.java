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

import java.io.IOException;
import java.nio.charset.Charset;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.salesforce.dto.generated.blng__Invoice__c_Lookup;
import org.apache.camel.component.salesforce.dto.generated.blng__Payment__c;
import org.apache.commons.io.IOUtils;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SObjectCompositeTest {

    private final SObjectComposite composite;

    public SObjectCompositeTest() {
        composite = new SObjectComposite("41.0", true);

        // first insert operation via an external id
        final blng__Invoice__c_Lookup invoiceLookup = new blng__Invoice__c_Lookup();
        invoiceLookup.setInvoice_External_Id__c("0116");

        final blng__Payment__c payment = new blng__Payment__c();
        payment.setblng__Invoice__r(invoiceLookup);

        composite.addCreate(payment, "NewPayment1");
        composite.addCreate(payment, "NewPayment2");
    }

    @Test
    public void shouldSerializeToJson() throws IOException {

        String expectedJson = IOUtils.toString(
                                               this.getClass().getResourceAsStream("/org/apache/camel/component/salesforce/api/dto/composite_request_example.json"),
                                               Charset.forName("UTF-8"));

        final ObjectMapper mapper = new ObjectMapper();

        final String serialized = mapper.writerFor(SObjectComposite.class).writeValueAsString(composite);

        assertEquals("Should serialize as expected by Salesforce", 
                     expectedJson.replaceAll(System.getProperty("line.separator"), "").replaceAll(" ", ""),
                     serialized);
    }
}
