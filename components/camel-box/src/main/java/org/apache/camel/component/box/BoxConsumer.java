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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.box.boxjavalibv2.dao.BoxEventCollection;
import com.box.boxjavalibv2.dao.BoxTypedObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.box.internal.BoxConstants;
import org.apache.camel.component.box.internal.CachedBoxClient;
import org.apache.camel.component.box.internal.EventCallback;
import org.apache.camel.component.box.internal.LongPollingEventsManager;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.ApiConsumerHelper;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodHelper;
import org.apache.camel.util.component.PropertyNamesInterceptor;
import org.apache.camel.util.component.ResultInterceptor;

/**
 * The Box consumer.
 */
//public class BoxConsumer extends AbstractApiConsumer<BoxApiName, BoxConfiguration> {
public class BoxConsumer extends DefaultConsumer
    implements PropertyNamesInterceptor, ResultInterceptor, EventCallback {

    private static final String CALLBACK_PROPERTY = "callback";

    private final LongPollingEventsManager apiProxy;
    private final Map<String, Object> properties;
    private final ApiMethod apiMethod;

    private boolean splitResult = true;
    private CachedBoxClient cachedBoxClient;

    public BoxConsumer(BoxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        apiMethod = ApiConsumerHelper.findMethod(endpoint, this);

        properties = new HashMap<String, Object>();
        properties.putAll(endpoint.getEndpointProperties());
        properties.put(CALLBACK_PROPERTY, this);

        // invoke LongPollingEventsManager.poll to start event polling
        cachedBoxClient = endpoint.getBoxClient();
        apiProxy = new LongPollingEventsManager(cachedBoxClient,
            endpoint.getConfiguration().getHttpParams(), endpoint.getExecutorService());
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        propertyNames.add(CALLBACK_PROPERTY);
    }

    @Override
    public void onEvent(BoxEventCollection events) throws Exception {
        // convert Events to exchange and process
        log.debug("Processed {} events for {}",
            ApiConsumerHelper.getResultsProcessed(this, events, splitResult), cachedBoxClient);
    }

    @Override
    public void onException(Exception e) {
        getExceptionHandler().handleException(ObjectHelper.wrapRuntimeCamelException(e));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // invoke the API method to start polling
        ApiMethodHelper.invokeMethod(apiProxy, apiMethod, properties);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        apiProxy.stopPolling();
    }

    public boolean isSplitResult() {
        return splitResult;
    }

    public void setSplitResult(boolean splitResult) {
        this.splitResult = splitResult;
    }

    @Override
    public Object splitResult(Object result) {
        if (result instanceof BoxEventCollection && splitResult) {
            BoxEventCollection eventCollection = (BoxEventCollection) result;
            final ArrayList<BoxTypedObject> entries = eventCollection.getEntries();
            return entries.toArray(new BoxTypedObject[entries.size()]);
        }
        return result;
    }

    @Override
    public void interceptResult(Object result, Exchange resultExchange) {
        if (result instanceof BoxEventCollection) {
            BoxEventCollection boxEventCollection = (BoxEventCollection) result;
            resultExchange.getIn().setHeader(BoxConstants.CHUNK_SIZE_PROPERTY,
                boxEventCollection.getChunkSize());
            resultExchange.getIn().setHeader(BoxConstants.NEXT_STREAM_POSITION_PROPERTY,
                boxEventCollection.getNextStreamPosition());
        }
    }
}
