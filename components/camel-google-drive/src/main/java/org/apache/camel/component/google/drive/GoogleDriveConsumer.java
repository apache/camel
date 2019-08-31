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
package org.apache.camel.component.google.drive;

import java.util.Map;
import java.util.Map.Entry;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GoogleDrive consumer.
 */
public class GoogleDriveConsumer extends AbstractApiConsumer<GoogleDriveApiName, GoogleDriveConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(BatchGoogleDriveClientFactory.class);
    
    public GoogleDriveConsumer(GoogleDriveEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    } 
    
    @Override
    protected Object doInvokeMethod(Map<String, Object> properties) throws RuntimeCamelException {
        AbstractGoogleClientRequest request = (AbstractGoogleClientRequest) super.doInvokeMethod(properties);
        try {
            BeanIntrospection beanIntrospection = getEndpoint().getCamelContext().adapt(ExtendedCamelContext.class).getBeanIntrospection();
            for (Entry<String, Object> p : properties.entrySet()) {
                beanIntrospection.setProperty(getEndpoint().getCamelContext(), request, p.getKey(), p.getValue());
            }
            return request.execute();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }      

}
