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

import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.apache.camel.util.IOHelper;

/**
 * The GoogleDrive consumer.
 */
public class GoogleDriveConsumer extends AbstractApiConsumer<GoogleDriveApiName, GoogleDriveConfiguration> {
    public GoogleDriveConsumer(GoogleDriveEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected Object doInvokeMethod(Map<String, Object> properties) throws RuntimeCamelException {
        AbstractGoogleClientRequest<?> request = (AbstractGoogleClientRequest<?>) super.doInvokeMethod(properties);
        try {
            BeanIntrospection beanIntrospection
                    = PluginHelper.getBeanIntrospection(getEndpoint().getCamelContext());
            for (Entry<String, Object> p : properties.entrySet()) {
                beanIntrospection.setProperty(getEndpoint().getCamelContext(), request, p.getKey(), p.getValue());
            }
            return GoogleDriveRequestHelper.execute(request);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void interceptResult(Object result, Exchange resultExchange) {
        if (result instanceof InputStream content) {
            // the content of a media request, such as drive-files/export, holds the connection: close it when the
            // exchange is done, whether the route read it or not
            resultExchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    IOHelper.close(content);
                }
            });
        }
    }

}
