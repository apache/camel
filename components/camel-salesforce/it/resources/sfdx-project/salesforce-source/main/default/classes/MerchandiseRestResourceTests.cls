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
@isTest
public class MerchandiseRestResourceTests {

    static testmethod void testDoGetWithPathParameter() {
        Merchandise__c merchandise = new Merchandise__c();
        merchandise.Price__c = 1;
        merchandise.Total_Inventory__c = 2;

        insert merchandise;

        RestRequest request = new RestRequest();
        request.requestURI = 'Merchandise/' + merchandise.Id;

        RestContext.request = request;

        MerchandiseRestResource.doGet();
    }

    static testmethod void testDoGetWithRequestParameter() {
        Merchandise__c merchandise = new Merchandise__c();
        merchandise.Price__c = 1;
        merchandise.Total_Inventory__c = 2;

        insert merchandise;

        RestRequest request = new RestRequest();
        request.requestURI = 'Merchandise/';
        request.params.put('id', merchandise.Id);        

        RestContext.request = request;

        MerchandiseRestResource.doGet();
    }

    static testmethod void testDoGetWithoutParameter() {
        RestRequest request = new RestRequest();
        request.requestURI = 'Merchandise/';

        RestContext.request = request;

        try {
            MerchandiseRestResource.doGet();
        } catch (Exception e) {
            // expected
        }
    }

    static testmethod void testDoPatch() {
        Merchandise__c merchandise = new Merchandise__c();
        merchandise.Price__c = 1;
        merchandise.Total_Inventory__c = 2;

        insert merchandise;

        MerchandiseRestResource.doPatch(merchandise);
    }
}
