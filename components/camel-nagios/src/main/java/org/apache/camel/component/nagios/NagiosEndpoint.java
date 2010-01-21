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

import com.googlecode.jsendnsca.core.NagiosPassiveCheckSender;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision$
 */
public class NagiosEndpoint extends DefaultEndpoint {

    private NagiosConfiguration configuration;
    private NagiosPassiveCheckSender sender;

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

    public NagiosPassiveCheckSender getSender() {
        if (sender == null) {
            sender = new NagiosPassiveCheckSender(getConfiguration().getNagiosSettings());
        }
        return sender;
    }

    public void setSender(NagiosPassiveCheckSender sender) {
        this.sender = sender;
    }
}
