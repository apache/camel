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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;

public class SObjectCollection implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean allOrNone;
    private List<AbstractDescribedSObjectBase> records = new ArrayList<>();

    public boolean isAllOrNone() {
        return allOrNone;
    }

    public void setAllOrNone(boolean allOrNone) {
        this.allOrNone = allOrNone;
    }

    public List<AbstractDescribedSObjectBase> getRecords() {
        return records;
    }

    public void setRecords(List<AbstractDescribedSObjectBase> records) {
        this.records = records;
    }
}
