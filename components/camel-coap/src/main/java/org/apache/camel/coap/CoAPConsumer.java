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
package org.apache.camel.coap;

import java.util.Iterator;

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.californium.core.server.resources.Resource;

/**
 * The CoAP consumer.
 */
public class CoAPConsumer extends DefaultConsumer {
    private final CoAPEndpoint endpoint;

    public CoAPConsumer(final CoAPEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public CoAPEndpoint getCoapEndpoint() {
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Iterator<String> pathSegmentIterator = endpoint.getPathSegmentsFromURI().iterator();
        Resource cr = endpoint.getCoapServer().getRoot();
        while (pathSegmentIterator.hasNext()) {
            String pathSegment = pathSegmentIterator.next();
            Resource child = cr.getChild(pathSegment);
            if (child == null) {
                child = new CamelCoapResource(pathSegment, this);
                ((CamelCoapResource) child).setObservable(endpoint.isObservable());
                cr.add(child);
                cr = child;
            } else if (!pathSegmentIterator.hasNext()) {
                ((CamelCoapResource) child).addConsumer(this);
            } else {
                cr = child;
            }
        }
    }
}
