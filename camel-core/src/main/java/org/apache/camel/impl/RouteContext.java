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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.model.FromType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.CompositeProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The context used to activate new routing rules
 *
 * @version $Revision: $
 */
public class RouteContext {
    private final RouteType route;
    private final FromType from;
    private final Endpoint endpoint;

    public RouteContext(RouteType route, FromType from, Endpoint endpoint) {
        this.route = route;
        this.from = from;
        this.endpoint = endpoint;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public FromType getFrom() {
        return from;
    }

    public RouteType getRoute() {
        return route;
    }

    public CamelContext getCamelContext() {
        return getRoute().getCamelContext();
    }

    public Processor createProcessor(List<ProcessorType> processors) {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorType output : processors) {
            Processor processor = output.createProcessor(this);
            list.add(processor);
        }
        if (list.size() == 0) {
            return null;
        }
        Processor processor;
        if (list.size() == 1) {
            processor = list.get(0);
        }
        else {
            processor = new CompositeProcessor(list);
        }
        return processor;
    }


    public Endpoint resolveEndpoint(String uri) {
        return route.resolveEndpoint(uri);
    }
}
