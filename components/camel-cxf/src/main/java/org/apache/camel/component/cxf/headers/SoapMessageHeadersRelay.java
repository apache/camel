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

package org.apache.camel.component.cxf.headers;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.cxf.binding.soap.SoapBindingConstants;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.SoapVersionFactory;
import org.apache.cxf.headers.Header;

public class SoapMessageHeadersRelay extends DefaultMessageHeadersRelay {

    private static final List<String> ACTIVATION_NS = 
        Arrays.asList(SoapBindingConstants.SOAP11_BINDING_ID, 
                      SoapBindingFactory.SOAP_11_BINDING, 
                      SoapBindingFactory.SOAP_12_BINDING);
    
    public SoapMessageHeadersRelay() {
    }
    
    public List<String> getActivationNamespaces() {
        return ACTIVATION_NS;
    }

    public void relay(Direction direction, List<Header> from, List<Header> to) {
        for (Iterator<Header> it = from.iterator(); it.hasNext();) {
            Header header = it.next();
            if (!(header instanceof SoapHeader)) {
                continue;
            }
            SoapHeader soapHeader = SoapHeader.class.cast(header);
            boolean dropped = false;
            for (Iterator<SoapVersion> itv = SoapVersionFactory.getInstance().getVersions(); 
                 itv.hasNext();) {
                SoapVersion version = itv.next();
                if (soapHeader.getActor() != null 
                    && soapHeader.getActor().equals(version.getNextRole())) {
                    // dropping headers if actor/role equals to {ns}/role|actor/next
                    // cxf SoapHeader needs to have soap:header@relay attribute, 
                    // then we can check for it here as well
                    it.remove();
                    dropped = true;
                    break;
                }
            }
            if (!dropped) {
                to.add(header);
            }
        }
    }
}
