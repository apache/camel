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
package org.apache.camel.component.cxf.interceptors;

import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingOperationInfo;

//closes UnitOfWork in good case
public class UnitOfWorkCloserInterceptor extends AbstractPhaseInterceptor<Message> {
    boolean handleOneWayMessage;

    public UnitOfWorkCloserInterceptor(String phase, boolean handleOneWayMessage) {
        super(phase);
        // Just make sure this interceptor is add after the OutgoingChainInterceptor
        if (phase.equals(Phase.POST_INVOKE)) {
            addAfter(OutgoingChainInterceptor.class.getName());
        }
        this.handleOneWayMessage = handleOneWayMessage;
    }

    public UnitOfWorkCloserInterceptor() {
        super(Phase.POST_LOGICAL_ENDING);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        if (handleOneWayMessage) {
            if (isOneWay(message)) {
                CxfUtils.closeCamelUnitOfWork(message);
            }
        } else { // Just do the normal process
            CxfUtils.closeCamelUnitOfWork(message);
        }
    }

    private boolean isOneWay(Message message) {
        Exchange ex = message.getExchange();
        BindingOperationInfo binding = ex.getBindingOperationInfo();
        if (null != binding && null != binding.getOperationInfo() && binding.getOperationInfo().isOneWay()) {
            return true;
        }
        return false;
    }
}
