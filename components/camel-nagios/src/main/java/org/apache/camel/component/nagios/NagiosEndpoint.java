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
package org.apache.camel.component.nagios;

import com.googlecode.jsendnsca.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.NonBlockingNagiosPassiveCheckSender;
import com.googlecode.jsendnsca.PassiveCheckSender;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * To send passive checks to Nagios using JSendNSCA.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "nagios", title = "Nagios", syntax = "nagios:host:port", producerOnly = true, label = "monitoring")
public class NagiosEndpoint extends DefaultEndpoint {

    private PassiveCheckSender sender;
    @UriParam
    private NagiosConfiguration configuration;
    @UriParam(defaultValue = "true")
    private boolean sendSync = true;

    public NagiosEndpoint() {
    }

    public NagiosEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new NagiosProducer(this, getSender());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Nagios consumer not supported");
    }

    public NagiosConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NagiosConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean isSendSync() {
        return sendSync;
    }

    /**
     * Whether or not to use synchronous when sending a passive check.
     * Setting it to false will allow Camel to continue routing the message and the passive check message will be send asynchronously.
     */
    public void setSendSync(boolean sendSync) {
        this.sendSync = sendSync;
    }

    public PassiveCheckSender getSender() {
        return sender;
    }

    public void setSender(PassiveCheckSender sender) {
        this.sender = sender;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (sender == null) {
            if (isSendSync()) {
                sender = new NagiosPassiveCheckSender(getConfiguration().getOrCreateNagiosSettings());
            } else {
                // use a non blocking sender
                sender = new NonBlockingNagiosPassiveCheckSender(getConfiguration().getOrCreateNagiosSettings());
            }
        }
    }
}
