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

import com.googlecode.jsendnsca.core.INagiosPassiveCheckSender;
import com.googlecode.jsendnsca.core.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.core.NonBlockingNagiosPassiveCheckSender;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@UriEndpoint(scheme = "nagios", title = "Nagios", syntax = "nagios:host:port", producerOnly = true, label = "monitoring")
public class NagiosEndpoint extends DefaultEndpoint {

    private INagiosPassiveCheckSender sender;
    @UriParam
    private NagiosConfiguration configuration;
    @UriParam(defaultValue = "true")
    private boolean sendSync = true;

    public NagiosEndpoint() {
    }

    public NagiosEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new NagiosProducer(this, getSender());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Nagios consumer not supported");
    }

    public boolean isSingleton() {
        return true;
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

    public synchronized INagiosPassiveCheckSender getSender() {
        if (sender == null) {
            if (isSendSync()) {
                sender = new NagiosPassiveCheckSender(getConfiguration().getNagiosSettings());
            } else {
                // use a non blocking sender
                sender = new NonBlockingNagiosPassiveCheckSender(getConfiguration().getNagiosSettings());
            }
        }
        return sender;
    }

    public void setSender(INagiosPassiveCheckSender sender) {
        this.sender = sender;
    }
}
