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

public class Dhis2Get {
    private final Dhis2Client dhis2Client;

    public Dhis2Get(Dhis2Client dhis2Client) {
        this.dhis2Client = dhis2Client;
    }

    public InputStream resource(
            String path, String fields, String filter, RootJunctionEnum rootJunction,
            Map<String, Object> queryParams) {
        GetOperation getOperation = newGetOperation(path, fields, filter, rootJunction, queryParams);

        return getOperation.withParameter("paging", "false").transfer().read();
    }

    protected GetOperation newGetOperation(
            String path, String fields, String filter, RootJunctionEnum rootJunction,
            Map<String, Object> queryParams) {
        GetOperation getOperation = dhis2Client.get(path);
        if (fields != null) {
            getOperation.withFields(fields);
        }

        if (filter != null) {
            getOperation.withFilter(filter);
        }

        if (rootJunction != null) {
            if (rootJunction.equals(RootJunctionEnum.AND)) {
                getOperation.withAndRootJunction();
            } else {
                getOperation.withOrRootJunction();
            }
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

    public Iterator<Dhis2Resource> collection(
            String path, String arrayName, Boolean paging, String fields, String filter,
            RootJunctionEnum rootJunction,
            Map<String, Object> queryParams) {
        GetOperation getOperation = newGetOperation(path, fields, filter, rootJunction, queryParams);

        IterableDhis2Response iteratorDhis2Response;
        if (paging != null && paging) {
            iteratorDhis2Response = getOperation.withPaging().transfer();
        } else {
            iteratorDhis2Response = getOperation.withoutPaging().transfer();
        }

        return iteratorDhis2Response.returnAs(Dhis2Resource.class, arrayName).iterator();
    }

}
