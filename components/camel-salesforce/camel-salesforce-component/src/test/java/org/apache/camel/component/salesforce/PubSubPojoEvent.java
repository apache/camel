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
package org.apache.camel.component.salesforce;

import java.util.Objects;

public class PubSubPojoEvent {
    private String Message__c;
    private long CreatedDate;
    private String CreatedById;

    @Override
    public String toString() {
        return "PubSubPojoEvent{" +
               "Message__c='" + Message__c + '\'' +
               ", CreatedDate=" + CreatedDate +
               ", CreatedById='" + CreatedById + '\'' +
               '}';
    }

    public String getMessage__c() {
        return Message__c;
    }

    public void setMessage__c(String message__c) {
        this.Message__c = message__c;
    }

    public long getCreatedDate() {
        return CreatedDate;
    }

    public void setCreatedDate(long createdDate) {
        this.CreatedDate = createdDate;
    }

    public String getCreatedById() {
        return CreatedById;
    }

    public void setCreatedById(String createdById) {
        this.CreatedById = createdById;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PubSubPojoEvent))
            return false;
        PubSubPojoEvent that = (PubSubPojoEvent) o;
        return CreatedDate == that.CreatedDate && Objects.equals(Message__c, that.Message__c)
                && CreatedById.equals(that.CreatedById);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Message__c, CreatedDate, CreatedById);
    }
}
