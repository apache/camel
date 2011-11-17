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
package org.apache.camel.component.apns;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.apns.model.InactiveDevice;
import org.apache.camel.impl.ScheduledPollConsumer;

public class ApnsConsumer extends ScheduledPollConsumer {

    private static final int DEFAULT_CONSUME_INITIAL_DELAY = 10;
    private static final int DEFAULT_CONSUME_DELAY = 3600;
    private static final boolean DEFAULT_APNS_FIXED_DELAY = true;

    public ApnsConsumer(ApnsEndpoint apnsEndpoint, Processor processor) {
        super(apnsEndpoint, processor);

        setInitialDelay(DEFAULT_CONSUME_INITIAL_DELAY);
        setDelay(DEFAULT_CONSUME_DELAY);
        setUseFixedDelay(DEFAULT_APNS_FIXED_DELAY);
    }

    protected int poll() throws Exception {
        List<InactiveDevice> inactiveDeviceList = getInactiveDevices();

        Iterator<InactiveDevice> it = inactiveDeviceList.iterator();

        while (it.hasNext()) {
            InactiveDevice inactiveDevice = it.next();

            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(inactiveDevice);
            getProcessor().process(e);
        }

        return inactiveDeviceList.size();
    }

    private List<InactiveDevice> getInactiveDevices() {
        ApnsEndpoint ae = getEndpoint();

        Map<String, Date> inactiveDeviceMap = ae.getApnsService().getInactiveDevices();

        List<InactiveDevice> inactiveDeviceList = new ArrayList<InactiveDevice>();
        for (Entry<String, Date> inactiveDeviceEntry : inactiveDeviceMap.entrySet()) {
            String deviceToken = inactiveDeviceEntry.getKey();
            Date date = inactiveDeviceEntry.getValue();

            InactiveDevice inactiveDevice = new InactiveDevice(deviceToken, date);
            inactiveDeviceList.add(inactiveDevice);
        }

        return inactiveDeviceList;
    }

    @Override
    public ApnsEndpoint getEndpoint() {
        return (ApnsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        // only add as consumer if not already registered
        if (!getEndpoint().getConsumers().contains(this)) {
            if (!getEndpoint().getConsumers().isEmpty()) {
                throw new IllegalStateException("Endpoint " + getEndpoint().getEndpointUri() + " only allows 1 active consumer but you attempted to start a 2nd consumer.");
            }
            getEndpoint().getConsumers().add(this);
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getEndpoint().getConsumers().remove(this);
    }

}
