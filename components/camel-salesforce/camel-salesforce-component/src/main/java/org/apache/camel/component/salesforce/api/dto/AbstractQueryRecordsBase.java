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
package org.apache.camel.component.salesforce.api.dto;

/**
 * Abstract base DTO for Salesforce SOQL Query records.
 * <p>
 * Derived classes must follow the template below:
 * </p>
 * <pre>
 * {@code
 * public class QueryResultMySObject extends AbstractQueryRecordsBase {
 *     @XStreamImplicit
 *     private List<MySObject> records;
 *
 *     public List<MySObject> getRecords() {
 *         return records;
 *     }
 *
 *     public void setRecords(List<MySObject> records) {
 *         this.records = records;
 *     }
 *
 * }
 * }
 * </pre>
 */
public abstract class AbstractQueryRecordsBase extends AbstractDTOBase {

    private Boolean done;
    private int totalSize;
    private String nextRecordsUrl;

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public String getNextRecordsUrl() {
        return nextRecordsUrl;
    }

    public void setNextRecordsUrl(String nextRecordsUrl) {
        this.nextRecordsUrl = nextRecordsUrl;
    }

}
