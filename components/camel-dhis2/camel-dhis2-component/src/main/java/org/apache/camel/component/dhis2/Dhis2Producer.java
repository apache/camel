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
package org.apache.camel.component.dhis2;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.component.dhis2.internal.Dhis2PropertiesHelper;
import org.apache.camel.support.component.AbstractApiProducer;
import org.apache.camel.support.component.ApiMethod;

public class Dhis2Producer extends AbstractApiProducer<Dhis2ApiName, Dhis2Configuration> {

    private static final String RESOURCE = "resource";

    public Dhis2Producer(Dhis2Endpoint endpoint) {
        super(endpoint, Dhis2PropertiesHelper.getHelper(endpoint.getCamelContext()));
    }

    @Override
    protected ApiMethod findMethod(Exchange exchange, Map<String, Object> properties) {
        ApiMethod apiMethod = super.findMethod(exchange, properties);
        if (apiMethod == null) {
            return null;
        }
        // the body is the resource of post and put; delete sends one only when given by inBody, a header or an option
        Dhis2ApiName apiName = endpoint.getApiName();
        if (!properties.containsKey(RESOURCE) && (apiName == Dhis2ApiName.POST || apiName == Dhis2ApiName.PUT)) {
            properties.put(RESOURCE, exchange.getIn().getBody());
        }
        Object resource = properties.get(RESOURCE);
        if (isJsonPayload(resource)) {
            // send the JSON as it is: the DHIS2 client would serialize bytes as a base64 string and a file as its path
            try {
                properties.put(RESOURCE, exchange.getContext().getTypeConverter()
                        .mandatoryConvertTo(String.class, exchange, resource));
            } catch (NoTypeConversionAvailableException e) {
                exchange.setException(e);
                return null;
            }
        }
        return apiMethod;
    }

    private static boolean isJsonPayload(Object resource) {
        return resource instanceof byte[] || resource instanceof InputStream || resource instanceof Reader
                || resource instanceof File || resource instanceof Path || resource instanceof WrappedFile
                || resource instanceof StreamCache;
    }

}
