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

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;

public class DefaultPlatformHttpConsumer extends DefaultConsumer
        implements PlatformHttpConsumer, PlatformHttpConsumerAware, Suspendable, SuspendableService {

    private PlatformHttpConsumer platformHttpConsumer;
    private boolean register = true;
    private AfterPropertiesConfigured afterConfiguredListener;

    public DefaultPlatformHttpConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public boolean isHostedService() {
        return true;
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    public PlatformHttpComponent getComponent() {
        return getEndpoint().getComponent();
    }

    public boolean isRegister() {
        return register;
    }

    public void setRegister(boolean register) {
        this.register = register;
    }

    @Override
    public PlatformHttpConsumer getPlatformHttpConsumer() {
        return platformHttpConsumer;
    }

    @Override
    public void registerAfterConfigured(AfterPropertiesConfigured listener) {
        this.afterConfiguredListener = listener;
    }

    @Override
    protected void doInit() throws Exception {
        platformHttpConsumer = getEndpoint().createPlatformHttpConsumer(getProcessor());
        configurePlatformHttpConsumer(platformHttpConsumer);
        super.doInit();

        ServiceHelper.initService(platformHttpConsumer);

        // signal that the platform-http consumer now has been configured
        // (rest-dsl can continue to initialize and start so it's ready when this consumer is started)
        if (afterConfiguredListener != null) {
            afterConfiguredListener.afterPropertiesConfigured(getEndpoint().getCamelContext());
        }
    }

    protected void configurePlatformHttpConsumer(PlatformHttpConsumer platformHttpConsumer) {
        // noop
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(platformHttpConsumer);
        if (register) {
            getComponent().addHttpEndpoint(getEndpoint().getPath(), getEndpoint().getHttpMethodRestrict(),
                    getEndpoint().getConsumes(), getEndpoint().getProduces(), platformHttpConsumer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (register) {
            getComponent().removeHttpEndpoint(getEndpoint().getPath());
        }
        ServiceHelper.stopAndShutdownServices(platformHttpConsumer);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(platformHttpConsumer);
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(platformHttpConsumer);
        super.doSuspend();
    }

}
