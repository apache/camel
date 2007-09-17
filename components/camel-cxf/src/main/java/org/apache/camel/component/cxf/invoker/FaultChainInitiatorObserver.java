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
package org.apache.camel.component.cxf.invoker;

import java.util.SortedSet;

import org.apache.camel.component.cxf.interceptors.FaultOutInterceptor;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.AbstractFaultChainInitiatorObserver;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptorChain;

public class FaultChainInitiatorObserver extends AbstractFaultChainInitiatorObserver {

    private SortedSet<Phase> phases;
    private boolean isOutbound;

    public FaultChainInitiatorObserver(Bus bus, SortedSet<Phase> phases, boolean isOutbound) {
        super(bus);
        this.phases = phases;
        this.isOutbound = isOutbound;
    }

    protected void initializeInterceptors(Exchange ex, PhaseInterceptorChain chain) {
        Endpoint e = ex.get(Endpoint.class);
        
        if (isOutboundObserver()) {
            chain.add(e.getOutFaultInterceptors());
            chain.add(e.getBinding().getOutFaultInterceptors());
            chain.add(e.getService().getOutFaultInterceptors());
            chain.add(getBus().getOutFaultInterceptors());
            chain.add(new FaultOutInterceptor());
        } else {
            chain.add(e.getBinding().getInFaultInterceptors());
            chain.add(e.getService().getInFaultInterceptors());
            chain.add(getBus().getInFaultInterceptors());
        }
    }
    
    @Override
    protected SortedSet<Phase> getPhases() {
        return phases;
    }

    @Override
    protected boolean isOutboundObserver() {
        return isOutbound;
    }

}
