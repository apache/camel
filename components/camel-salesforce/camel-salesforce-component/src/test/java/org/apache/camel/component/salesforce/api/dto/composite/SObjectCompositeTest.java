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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.camel.component.salesforce.dto.generated.Account_Lookup;
import org.apache.camel.component.salesforce.dto.generated.blng__Invoice__c_Lookup;
import org.apache.camel.component.salesforce.dto.generated.blng__PaymentAllocationInvoice__c;
import org.apache.camel.component.salesforce.dto.generated.blng__PaymentAllocationInvoice__c_blng__TypeEnum;
import org.apache.camel.component.salesforce.dto.generated.blng__Payment__c;
import org.apache.camel.component.salesforce.dto.generated.blng__Payment__c_Lookup;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SObjectCompositeTest {

    private final SObjectComposite composite;
    
    public SObjectCompositeTest() {
    	composite = new SObjectComposite("41.0", true);
    	
    	// first upsert operation via an external id
    	final Account_Lookup accountLookup = new Account_Lookup();
    	accountLookup.setMQ2C_ECR_Id__c("ECR-1");
    	final blng__Payment__c payment = new blng__Payment__c();
    	payment.setblng__Account__r(accountLookup);
    	payment.setblng__Amount__c(new Double(101));
    	
    	// second upsert operation via two external ids
    	final blng__Payment__c_Lookup paymentLookup = new blng__Payment__c_Lookup();
    	paymentLookup.setMQ2C_Payment_External_Id__c("121000386");
    	
    	final blng__Invoice__c_Lookup invoiceLookup = new blng__Invoice__c_Lookup();
    	invoiceLookup.setMQ2C_Invoice_External_Id__c("0116");
    	
    	final blng__PaymentAllocationInvoice__c paymentAllocation = new blng__PaymentAllocationInvoice__c();
    	paymentAllocation.setblng__Payment__r(paymentLookup);
    	paymentAllocation.setblng__Amount__c(new Double(10));
    	paymentAllocation.setblng__Type__c(blng__PaymentAllocationInvoice__c_blng__TypeEnum.ALLOCATION);
    	paymentAllocation.setblng__Invoice__r(invoiceLookup);
    	
    	composite.addUpsertByExternalId("blng__Payment__c", "MQ2C_Payment_External_Id__c", "121000386", payment, "UpsertPayment");
    	composite.addCreate(paymentAllocation, "NewPaymentAllocation");
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
