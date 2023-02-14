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
package org.apache.camel.component.dhis2.api;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.IterableDhis2Response;
import org.hisp.dhis.integration.sdk.api.operation.GetOperation;

/**
 * Sample API used by Dhis2 Component whose method signatures are read from Java source.
 */
public class Dhis2Get {
    private final Dhis2Client dhis2Client;

    public Dhis2Get(Dhis2Client dhis2Client) {
        this.dhis2Client = dhis2Client;
    }

    public InputStream resource(String path, String fields, String filter, Map<String, Object> queryParams) {
        GetOperation getOperation = newGetOperation(path, fields, filter, queryParams);

        return getOperation.withParameter("paging", "false").transfer().read();
    }

    protected GetOperation newGetOperation(String path, String fields, String filter, Map<String, Object> queryParams) {
        GetOperation getOperation = dhis2Client.get(path);
        if (fields != null) {
            getOperation.withFields(fields);
        }

        if (filter != null) {
            getOperation.withFilter(filter);
        }

        if (queryParams != null) {
            for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
                if (queryParam.getValue() instanceof List) {
                    for (String queryValue : (List<String>) queryParam.getValue()) {
                        getOperation.withParameter(queryParam.getKey(), queryValue);
                    }
                } else {
                    getOperation.withParameter(queryParam.getKey(), (String) queryParam.getValue());
                }
            }
        }

        return getOperation;
    }

    public <T> Iterator<T> collection(
            String path, String itemType, Boolean paging, String fields, String filter,
            Map<String, Object> queryParams) {
        GetOperation getOperation = newGetOperation(path, fields, filter, queryParams);
        Iterable<T> iterable;

        IterableDhis2Response iteratorDhis2Response;
        if (paging == null || paging) {
            iteratorDhis2Response = getOperation.withPaging().transfer();
        } else {
            iteratorDhis2Response = getOperation.withoutPaging().transfer();
        }

        if (itemType == null) {
            iterable = (Iterable<T>) iteratorDhis2Response
                    .returnAs(Map.class, path);
        } else {
            try {
                iterable = (Iterable<T>) iteratorDhis2Response
                        .returnAs(Class.forName(itemType), path);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        return iterable.iterator();
    }

}
