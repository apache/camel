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
package org.apache.camel.component.cxf.interceptors;


import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.xml.XMLFault;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.XMLMessage;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingOperationInfo;

public class FaultOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LogUtils.getL7dLogger(FaultOutInterceptor.class);

    public FaultOutInterceptor() {
        super(Phase.PREPARE_SEND);
    }

    @SuppressWarnings("unchecked")
    public void handleMessage(Message message) throws Fault {
        // To walk around the FaultOutInterceptor NPE issue of CXF 2.0.4
        checkBindingOperationInfor(message);

        Exception ex = message.getContent(Exception.class);

        if (ex != null) {
            if (!(ex instanceof Fault)) {
                ex = new Fault(ex);
            }

            if (message instanceof XMLMessage) {
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.info("FaultOutInterceptor Creating XMLFault");
                }
                ex = XMLFault.createFault((Fault)ex);
            } else if (message instanceof SoapMessage) {
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.info("FaultOutInterceptor Creating SoapFault");
                }

                SoapMessage sm = (SoapMessage)message;
                ex = SoapFault.createFault((Fault)ex, sm.getVersion());
            }
            message.setContent(Exception.class, ex);
        }
    }

    /*
     * This method is used to walk around the NPE issue of CXF 2.0.4
     * org.apache.cxf.interceptor.FaultOutInterceptor.
     * This issue was fixed in CXF 2.0.5 and CXF 2.1, when we upgrade CXF to that version
     * we could remove this method from the interceptor
     */
    private void checkBindingOperationInfor(Message message) {
        BindingOperationInfo bop = message.getExchange().get(BindingOperationInfo.class);
        if (bop == null) {
            bop = new FakeBindingOperationInfo();
            message.getExchange().put(BindingOperationInfo.class, bop);
        }


    }
}
