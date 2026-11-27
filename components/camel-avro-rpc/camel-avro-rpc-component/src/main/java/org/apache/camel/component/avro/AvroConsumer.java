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
package org.apache.camel.component.avro;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

public class AvroConsumer extends DefaultConsumer {

    public AvroConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public AvroEndpoint getEndpoint() {
        return (AvroEndpoint) super.getEndpoint();
    }

    private volatile boolean registered;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        AvroConfiguration configuration = getEndpoint().getConfiguration();
        ((AvroComponent) getEndpoint().getComponent()).register(configuration.getListenerKey(),
                configuration.getMessageName(), this);
        registered = true;
    }

    @Override
    protected void doStop() throws Exception {
        // a consumer that failed to register must not unregister the consumer that holds its message name
        if (registered) {
            registered = false;
            AvroConfiguration configuration = getEndpoint().getConfiguration();
            ((AvroComponent) getEndpoint().getComponent()).unregister(configuration.getListenerKey(),
                    configuration.getMessageName(), this);
        }
        super.doStop();
    }
}
