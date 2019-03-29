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
package org.apache.camel.dataformat.beanio;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Header implements BeanIOHeader {

    String identifier;
    String recordType;
    Date headerDate;

    public Header() {
    }

    public Header(String identifier, Date headerDate, String recordType) {
        this.identifier = identifier;
        this.headerDate = headerDate;
        this.recordType = recordType;
    }

    @Override
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (recordType != null ? recordType.hashCode() : 0);
        result = 31 * result + (headerDate != null ? headerDate.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj == this) {
            return true;
        } else {
            Header record = (Header) obj;
            return identifier.equals(record.getIdentifier()) && recordType.equals(record.getRecordType());
        }
    }

    @Override
    public String toString() {
        return "TYPE[" + this.recordType + "], IDENTIFIER[" + this.identifier + "]";
    }

    /**
     * @return the identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * @param identifier the identifier to set
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * @return the headerDate
     */
    public Date getHeaderDate() {
        return headerDate;
    }

    /**
     * @param headerDate the headerDate to set
     */
    public void setHeaderDate(Date headerDate) {
        this.headerDate = headerDate;
    }

    /**
     * @return the recordType
     */
    public String getRecordType() {
        return recordType;
    }

    /**
     * @param recordType the recordType to set
     */
    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    @Override
    public Map<String, Object> getHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(recordType + "Date", headerDate);
        return headers;
    }
}