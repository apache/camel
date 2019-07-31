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
package org.apache.camel.itest.customerrelations;


public class CustomerServiceV1Impl implements CustomerServiceV1 {
    public Customer getCustomer(String customerNumber) {
        Customer result = null;
        if ("12345".equals(customerNumber)) {
            Person maxMueller = new Person();
            maxMueller.setFirstname("Max");
            maxMueller.setLastname("Mueller");
            maxMueller.setGender(Gender.MALE);
            Address maxMuellerAddress = new Address();
            maxMuellerAddress.setLine1("Mussterstr. 10");
            maxMuellerAddress.setLine2("");
            maxMuellerAddress.setPostalCode("12345");
            maxMuellerAddress.setCity("Musterhausen");
            Customer maxMuellerCustomer = new Customer();
            maxMuellerCustomer.setPerson(maxMueller);
            maxMuellerCustomer.setAddress(maxMuellerAddress);
            result = maxMuellerCustomer;
        } else {
            throw new RuntimeException("No such customer");
        }
        return result;
    }
}
