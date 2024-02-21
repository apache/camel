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
package org.apache.camel.component.platform.http;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;

public class PlatformHttpConsumer extends DefaultConsumer implements Suspendable, SuspendableService {

    private Consumer delegatedConsumer;

    public PlatformHttpConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    public PlatformHttpComponent getComponent() {
        return getEndpoint().getComponent();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        delegatedConsumer = getEndpoint().createDelegateConsumer(getProcessor());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(delegatedConsumer);
        getComponent().addHttpEndpoint(getEndpoint().getPath(), getEndpoint().getHttpMethodRestrict(), delegatedConsumer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getComponent().removeHttpEndpoint(getEndpoint().getPath());
        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(delegatedConsumer);
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(delegatedConsumer);
        super.doSuspend();
    }

}
