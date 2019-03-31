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
package org.apache.camel.component.cxf.multipart;

import javax.xml.ws.Holder;

import org.apache.camel.cxf.multipart.MultiPartInvoke;
import org.apache.camel.cxf.multipart.types.InE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@javax.jws.WebService(
                      serviceName = "MultiPartInvokeService",
                      portName = "MultiPartInvokePort",
                      targetNamespace = "http://adapter.ti.tongtech.com/ws",
                      endpointInterface = "org.apache.camel.cxf.multipart.MultiPartInvoke")
                      
public class MultiPartInvokeImpl implements MultiPartInvoke {

    private static final Logger LOG = LoggerFactory.getLogger(MultiPartInvokeImpl.class);

    @Override
    public void foo(InE in, InE in1, Holder<InE> out, Holder<InE> out1) {
        LOG.info("Executing operation foo");
        LOG.info("{}", in);
        LOG.info("{}", in1);
        try {
            InE outValue = in;
            out.value = outValue;
            InE out1Value = in1;
            out1.value = out1Value;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

}
