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

import com.googlecode.jsendnsca.core.Level;
import com.googlecode.jsendnsca.core.MessagePayload;
import com.googlecode.jsendnsca.core.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.core.NagiosSettings;
import org.apache.camel.management.EventNotifierSupport;

/**
 * An {@link org.apache.camel.spi.EventNotifier} which sends alters to Nagios.
 *
 * @version $Revision$
 */
public class NagiosEventNotifier extends EventNotifierSupport {

    private NagiosSettings nagiosSettings;
    private NagiosConfiguration configuration;
    private NagiosPassiveCheckSender sender;

    public void notify(EventObject eventObject) throws Exception {
        // create message payload to send
        String message = eventObject.toString();
        MessagePayload payload = new MessagePayload("localhost", Level.CRITICAL.ordinal(), "Camel", message);

        if (log.isInfoEnabled()) {
            log.info("Sending notification to Nagios: " + payload.getMessage());
        }
        sender.send(payload);
        if (log.isTraceEnabled()) {
            log.trace("Sending notification done");
        }
    }

    // TODO: level should be computed based on event message
    // TODO: host and service name should be configurable
    

    public boolean isEnabled(EventObject eventObject) {
        return true;
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

    @Override
    protected void doStart() throws Exception {
        if (nagiosSettings == null) {
            nagiosSettings = configuration.getNagiosSettings();
        }
        sender = new NagiosPassiveCheckSender(nagiosSettings);

        log.info("Using " + configuration);
    }

    @Override
    protected void doStop() throws Exception {
        sender = null;
    }

}
