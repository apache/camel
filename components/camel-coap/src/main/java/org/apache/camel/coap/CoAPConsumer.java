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
package org.apache.camel.coap;


import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.Resource;

/**
 * The CoAP consumer.
 */
public class CoAPConsumer extends DefaultConsumer {
    private final CoAPEndpoint endpoint;
    private List<CoapResource> resources = new LinkedList<>();

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
        
        String path = endpoint.getUri().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Resource cr = endpoint.getCoapServer().getRoot();
        while (!path.isEmpty()) {
            int idx = path.indexOf('/');
            String part1 = path;
            if (idx != -1) {
                part1 = path.substring(0, idx);
                path = path.substring(idx + 1);
            } else {
                path = "";
            }
            Resource child = cr.getChild(part1);
            if (child == null) {
                child = new CamelCoapResource(part1, this);
                cr.add(child);
                cr = child;
            } else if (path.isEmpty()) {
                ((CamelCoapResource)child).addConsumer(this);
            } else {
                cr = child;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (CoapResource r : resources) {
            r.getParent().delete(r);
        }
        resources.clear();
        super.doStop();
    }
}
