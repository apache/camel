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
package org.apache.camel.component.avro;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

public class AvroConsumer extends DefaultConsumer {

    public AvroConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public AvroEndpoint getEndpoint() {
        return (AvroEndpoint) super.getEndpoint();
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ((AvroComponent)getEndpoint().getComponent()).register(getEndpoint().getConfiguration()
            .getUriAuthority(), getEndpoint().getConfiguration().getMessageName(), this);
    }

    @Override
    protected void doStop() throws Exception {
        ((AvroComponent) getEndpoint().getComponent()).unregister(getEndpoint().getConfiguration().getUriAuthority(), getEndpoint().getConfiguration().getMessageName());
        super.doStop();
    }
}
