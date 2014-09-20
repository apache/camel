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
package org.apache.camel.examples;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.apache.camel.component.jpa.PreConsumed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a task which is added to the database, then removed from the database when it is consumed
 *
 * @version 
 */
@Entity
public class SendEmail {
    private static final Logger LOG = LoggerFactory.getLogger(SendEmail.class);
    private Long id;
    private String address;

    public SendEmail() {
    }

    public SendEmail(String address) {
        setAddress(address);
    }

    @Id
    @GeneratedValue
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @PreConsumed
    public void doBefore() {
        LOG.info("Invoked the pre consumed method with address {}", getAddress());
        if ("dummy".equals(getAddress())) {
            setAddress("dummy@somewhere.org");
        }
    }

    @Override
    public String toString() {
        // OpenJPA warns about fields being accessed directly in methods if NOT using the corresponding getters.
        return "SendEmail[id: " + getId() + ", address: " + getAddress() + "]";
    }

}
