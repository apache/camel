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

import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.linkedin.api.LinkedInException;
import org.apache.camel.component.linkedin.api.model.Error;
import org.apache.camel.component.linkedin.internal.LinkedInApiName;
import org.apache.camel.component.linkedin.internal.LinkedInPropertiesHelper;
import org.apache.camel.util.component.AbstractApiProducer;
import org.apache.camel.util.component.ApiMethod;

/**
 * The LinkedIn producer.
 */
public class LinkedInProducer extends AbstractApiProducer<LinkedInApiName, LinkedInConfiguration> {

    public LinkedInProducer(LinkedInEndpoint endpoint) {
        super(endpoint, LinkedInPropertiesHelper.getHelper());
    }

    @Override
    protected Object doInvokeMethod(ApiMethod method, Map<String, Object> properties) throws RuntimeCamelException {
        try {
            return super.doInvokeMethod(method, properties);
        } catch (RuntimeCamelException e) {
            if (e.getCause() instanceof WebApplicationException) {
                final WebApplicationException cause = (WebApplicationException) e.getCause();
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
