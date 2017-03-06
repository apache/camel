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
package org.apache.camel.component.box;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxEvent;
import com.box.sdk.EventListener;

import org.apache.camel.Processor;
import org.apache.camel.component.box.api.BoxEventsManager;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiConsumer;
import org.apache.camel.util.component.ApiConsumerHelper;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodHelper;

/**
 * The Box consumer.
 */
public class BoxConsumer extends AbstractApiConsumer<BoxApiName, BoxConfiguration> implements EventListener {

    private static final String LISTENER_PROPERTY = "listener";

    private BoxAPIConnection boxConnection;

    private BoxEventsManager apiProxy;

    private final ApiMethod apiMethod;

    private final Map<String, Object> properties;

    public BoxConsumer(BoxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        apiMethod = ApiConsumerHelper.findMethod(endpoint, this);

        // Add listener property to register this consumer as listener for
        // events.
        properties = new HashMap<String, Object>();
        properties.putAll(endpoint.getEndpointProperties());
        properties.put(LISTENER_PROPERTY, this);

        boxConnection = endpoint.getBoxConnection();

        apiProxy = new BoxEventsManager(boxConnection);
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        propertyNames.add(LISTENER_PROPERTY);
    }

    @Override
    public void onEvent(BoxEvent event) {
        try {
            // Convert Events to exchange and process
            log.debug("Processed {} event for {}", ApiConsumerHelper.getResultsProcessed(this, event, false),
                    boxConnection);
        } catch (Exception e) {
            log.info("Received exception consuming event: ", e);
        }
    }

    @Override
    public void onNextPosition(long position) {
    }

    @Override
    public boolean onException(Throwable e) {
        getExceptionHandler().handleException(ObjectHelper.wrapRuntimeCamelException(e));
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // invoke the API method to start listening
        ApiMethodHelper.invokeMethod(apiProxy, apiMethod, properties);
    }

    @Override
    protected void doStop() throws Exception {
        apiProxy.stopListening();

        super.doStop();
    }

}
