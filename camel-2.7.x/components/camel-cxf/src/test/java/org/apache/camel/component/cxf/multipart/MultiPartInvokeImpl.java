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

package org.apache.camel.component.cxf.multipart;

import java.util.logging.Logger;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.Holder;

import org.apache.camel.cxf.multipart.MultiPartInvoke;
import org.apache.camel.cxf.multipart.types.InE;


@javax.jws.WebService(
                      serviceName = "MultiPartInvokeService",
                      portName = "MultiPartInvokePort",
                      targetNamespace = "http://adapter.ti.tongtech.com/ws",
                      endpointInterface = "org.apache.camel.cxf.multipart.MultiPartInvoke")
                      
public class MultiPartInvokeImpl implements MultiPartInvoke {

    private static final Logger LOG = Logger.getLogger(MultiPartInvokeImpl.class.getName());

    @Override
    public void foo(InE in, InE in1, Holder<InE> out, Holder<InE> out1) {
        LOG.info("Executing operation foo");
        System.out.println(in);
        System.out.println(in1);
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
