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
package org.apache.camel.component.google.drive;

import java.util.Map;
import java.util.Map.Entry;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.component.google.drive.internal.GoogleDriveConstants;
import org.apache.camel.component.google.drive.internal.GoogleDrivePropertiesHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.component.AbstractApiProducer;
import org.apache.camel.util.component.ApiMethod;

/**
 * The GoogleDrive producer.
 */
public class GoogleDriveProducer extends AbstractApiProducer<GoogleDriveApiName, GoogleDriveConfiguration> {

    public GoogleDriveProducer(GoogleDriveEndpoint endpoint) {
        super(endpoint, GoogleDrivePropertiesHelper.getHelper());
    }

    @Override
    protected Object doInvokeMethod(ApiMethod method, Map<String, Object> properties) throws RuntimeCamelException {
        AbstractGoogleClientRequest request = (AbstractGoogleClientRequest) super.doInvokeMethod(method, properties);
        try {
            TypeConverter typeConverter = getEndpoint().getCamelContext().getTypeConverter();
            for (Entry<String, Object> p : properties.entrySet()) {
                IntrospectionSupport.setProperty(typeConverter, request, p.getKey(), p.getValue());
            }
            return request.execute();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected String getThreadProfileName() {
        return GoogleDriveConstants.THREAD_PROFILE_NAME;
    }
    
}
