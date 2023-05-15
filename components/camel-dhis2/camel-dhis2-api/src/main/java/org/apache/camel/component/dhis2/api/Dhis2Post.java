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
import java.util.List;
import java.util.Map;

import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.operation.PostOperation;

public class Dhis2Post {
    private final Dhis2Client dhis2Client;

    public Dhis2Post(Dhis2Client dhis2Client) {
        this.dhis2Client = dhis2Client;
    }

    public InputStream resource(String path, Object resource, Map<String, Object> queryParams) {
        PostOperation postOperation = dhis2Client.post(path);
        if (queryParams != null) {
            for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
                if (queryParam.getValue() instanceof List) {
                    for (String queryValue : (List<String>) queryParam.getValue()) {
                        postOperation.withParameter(queryParam.getKey(), queryValue);
                    }
                } else {
                    postOperation.withParameter(queryParam.getKey(), (String) queryParam.getValue());
                }
            }
        }

        if (resource != null) {
            postOperation.withResource(resource);
        }

        return postOperation.transfer().read();

    }
}
