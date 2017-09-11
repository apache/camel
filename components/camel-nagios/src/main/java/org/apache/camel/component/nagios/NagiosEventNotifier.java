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
package org.apache.camel.component.nagios;

import java.util.EventObject;

import com.googlecode.jsendnsca.Level;
import com.googlecode.jsendnsca.MessagePayload;
import com.googlecode.jsendnsca.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.NagiosSettings;
import com.googlecode.jsendnsca.PassiveCheckSender;

import org.apache.camel.management.event.CamelContextStartupFailureEvent;
import org.apache.camel.management.event.CamelContextStopFailureEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ServiceStartupFailureEvent;
import org.apache.camel.management.event.ServiceStopFailureEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * An {@link org.apache.camel.spi.EventNotifier} which sends alters to Nagios.
 *
 * @version 
 */
public class NagiosEventNotifier extends EventNotifierSupport {

    private NagiosSettings nagiosSettings;
    private NagiosConfiguration configuration;
    private PassiveCheckSender sender;
    private String serviceName = "Camel";
    private String hostName = "localhost";

    public NagiosEventNotifier() {

    }

    public NagiosEventNotifier(PassiveCheckSender sender) {
        this.sender = sender;
    }

    public void notify(EventObject eventObject) throws Exception {
        // create message payload to send
        String message = eventObject.toString();
        Level level = determineLevel(eventObject);
        MessagePayload payload = new MessagePayload(getHostName(), level, getServiceName(), message);

        if (log.isInfoEnabled()) {
            log.info("Sending notification to Nagios: {}", payload.getMessage());
        }
        sender.send(payload);
        log.trace("Sending notification done");
    }

    public boolean isEnabled(EventObject eventObject) {
        return true;
    }

    protected Level determineLevel(EventObject eventObject) {
        // failures is considered critical
        if (eventObject instanceof ExchangeFailedEvent
                || eventObject instanceof CamelContextStartupFailureEvent
                || eventObject instanceof CamelContextStopFailureEvent
                || eventObject instanceof ServiceStartupFailureEvent
                || eventObject instanceof ServiceStopFailureEvent) {
            return Level.CRITICAL;
        }

        // the failure was handled so its just a warning
        // and warn when a redelivery attempt is done
        if (eventObject instanceof ExchangeFailureHandledEvent
                || eventObject instanceof ExchangeRedeliveryEvent) {
            return Level.WARNING;
        }

        // default to OK
        return Level.OK;
    }

    public NagiosConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new NagiosConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(NagiosConfiguration configuration) {
        this.configuration = configuration;
    }

    public NagiosSettings getNagiosSettings() {
        return nagiosSettings;
    }

    public void setNagiosSettings(NagiosSettings nagiosSettings) {
        this.nagiosSettings = nagiosSettings;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    @Override
    protected void doStart() throws Exception {
        if (nagiosSettings == null) {
            nagiosSettings = configuration.getNagiosSettings();
        }
        if (sender == null) {
            sender = new NagiosPassiveCheckSender(nagiosSettings);
        }

        log.info("Using " + configuration);
    }

    @Override
    protected void doStop() throws Exception {
        sender = null;
    }

}
