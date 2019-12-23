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
package org.apache.camel.component.apns;

import java.util.Map;

import com.notnoop.apns.ApnsService;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For sending notifications to Apple iOS devices
 */
@Component("apns")
public class ApnsComponent extends DefaultComponent {

    @Metadata(required = true)
    private ApnsService apnsService;

    public ApnsComponent() {
    }

    public ApnsComponent(ApnsService apnsService) {
        this();
        this.apnsService = apnsService;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ApnsEndpoint endpoint = new ApnsEndpoint(uri, this);
        endpoint.setName(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ApnsService getApnsService() {
        return apnsService;
    }

    /**
     * The ApnsService to use.
     * <p/>
     * The {@link org.apache.camel.component.apns.factory.ApnsServiceFactory} can be used to build a {@link ApnsService}
     */
    public void setApnsService(ApnsService apnsService) {
        this.apnsService = apnsService;
    }

}
