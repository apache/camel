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
@RestResource(urlMapping='/Merchandise/*')
global with sharing class MerchandiseRestResource {

    @HttpGet
    global static Merchandise__c doGet() {
        RestRequest req = RestContext.request;
        String merchandiseId = null;
        if (!req.requestURI.endsWith('/')) {
            merchandiseId = req.requestURI.substring(req.requestURI.lastIndexOf('/')+1);
        } else if (req.params.get('id') != null) {
            merchandiseId = req.params.get('id');
        }
        if (merchandiseId != null) {
            Merchandise__c merchandise = [SELECT Id, Name, Description__c, Price__c, Total_Inventory__c FROM Merchandise__c WHERE Id = :merchandiseId];
            return merchandise;
        } else {
            throw new InvalidParamException('Missing merchandise id in URL and query params');
        }
    }
  
    @HttpPatch
    global static Merchandise__c doPatch(Merchandise__c merchandise) {
        // lookup merchandise
        Merchandise__c current = [SELECT Id, Name, Description__c, Price__c, Total_Inventory__c FROM Merchandise__c WHERE Id = :merchandise.Id];
        if (current == null) {
            throw new InvalidParamException('Missing merchandise for id ' + merchandise.Id);
        }
        if (merchandise.Description__c != null) {
            current.Description__c = merchandise.Description__c;
        }
        if (merchandise.Price__c != null) {
            current.Price__c = merchandise.Price__c;
        }
        if (merchandise.Total_Inventory__c != null) {
            current.Total_Inventory__c = merchandise.Total_Inventory__c;
        }

        update current;
        return current;
    }

    // Invalid Merchandise Id exception
    public class InvalidParamException extends Exception {}
}