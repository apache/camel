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
package org.apache.camel.component.azure.servicebus;

import java.util.List;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.implementation.EventHubDescription;
import com.microsoft.windowsazure.services.servicebus.models.CreateEventHubResult;
import com.microsoft.windowsazure.services.servicebus.models.EventHubInfo;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

public class SbEventEndpoint extends AbstractSbEndpoint {
    public SbEventEndpoint(String uri, SbComponent component, SbConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractSbConsumer sbConsumer = new SbEventConsumer(this, processor);
        configureConsumer(sbConsumer);
        return sbConsumer;
    }
    @Override
    public Producer createProducer() throws Exception {
        return new SbEventProducer(this);
    }
    private void createEventHub() throws ServiceException {
        EventHubInfo newInfo = new EventHubInfo(configuration.getEventHubPath());
        EventHubDescription eventHubDescription = new EventHubDescription();

        if (configuration.getUserMetadata() != null) {
            eventHubDescription.setUserMetadata(configuration.getUserMetadata());
        }

        if (configuration.getDefaultMessageRetention() != null) {
            eventHubDescription.setDefaultMessageRetention(configuration.getDefaultMessageRetention());
        }

        newInfo.setModel(eventHubDescription);

        if (configuration.getUserMetadata() != null) {
            newInfo.setUserMetadata(configuration.getUserMetadata());
        }

        CreateEventHubResult createEventHubResult = client.createEventHub(newInfo);
        if (createEventHubResult.getValue() == null) {
            throw new ServiceException("Failed to create a event hub <" + configuration.getEventHubPath() + ">");
        }
    }

    private boolean isEventHubExisted() throws ServiceException {
        List<EventHubInfo> items = client.listEventHubs().getItems();

        for (EventHubInfo item : items) {
            if (item.getPath().equalsIgnoreCase(configuration.getEventHubPath())) {
                return true;
            }
        }

        return false;
    }
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!isEventHubExisted()) {
            createEventHub();
        }

    }
}
