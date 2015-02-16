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
package org.apache.camel.component.linkedin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.linkedin.api.LinkedInException;
import org.apache.camel.component.linkedin.api.model.Error;
import org.apache.camel.component.linkedin.internal.LinkedInApiName;
import org.apache.camel.util.component.AbstractApiConsumer;
import org.apache.camel.util.component.ApiMethod;

/**
 * The LinkedIn consumer.
 */
public class LinkedInConsumer extends AbstractApiConsumer<LinkedInApiName, LinkedInConfiguration> {

    public LinkedInConsumer(LinkedInEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        // do we need to add fields option
        if (!propertyNames.contains(LinkedInEndpoint.FIELDS_OPTION)) {
            final List<ApiMethod> candidates = endpoint.getCandidates();

            for (ApiMethod method : candidates) {
                if (!method.getArgNames().contains(LinkedInEndpoint.FIELDS_OPTION)) {
                    return;
                }
            }
            // all candidates use fields option, so there is no ambiguity
            propertyNames.add(LinkedInEndpoint.FIELDS_OPTION);
        }
    }

    @Override
    protected Object doInvokeMethod(Map<String, Object> args) {
        try {
            return super.doInvokeMethod(args);
        } catch (RuntimeCamelException e) {
            if (e.getCause() instanceof WebApplicationException) {
                WebApplicationException cause = (WebApplicationException) e.getCause();
                final Response response = cause.getResponse();
                if (response.hasEntity()) {
                    // try and convert it to LinkedInException
                    final org.apache.camel.component.linkedin.api.model.Error error = response.readEntity(Error.class);
                    throw new RuntimeCamelException(
                        String.format("Error invoking %s: %s", method.getName(), error.getMessage()),
                        new LinkedInException(error, response));
                }
            }
            throw e;
        }
    }
}
