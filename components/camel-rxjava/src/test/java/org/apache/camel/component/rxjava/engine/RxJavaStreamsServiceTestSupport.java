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
package org.apache.camel.component.rxjava.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConstants;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.impl.engine.PrototypeExchangeFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;

class RxJavaStreamsServiceTestSupport extends CamelTestSupport {
    protected CamelReactiveStreamsService crs;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // camel-rxjava does not work with pooled exchanges
        context.getCamelContextExtension().setExchangeFactory(new PrototypeExchangeFactory());

        context.addComponent(
                ReactiveStreamsConstants.SCHEME,
                ReactiveStreamsComponent.withServiceType(RxJavaStreamsConstants.SERVICE_NAME));

        return context;
    }

    @Override
    protected void doPostSetup() {
        this.crs = CamelReactiveStreams.get(context);
    }

    @Override
    public boolean isUseRouteBuilder() {
        // You need to start the context if "use route builder" is set to false
        return false;
    }

    protected ReactiveStreamsComponent getReactiveStreamsComponent() {
        return ObjectHelper.notNull(
                context.getComponent(ReactiveStreamsConstants.SCHEME, ReactiveStreamsComponent.class),
                ReactiveStreamsConstants.SCHEME);
    }
}
