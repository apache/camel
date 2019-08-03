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
package org.apache.camel.component.cxf.common.header;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.spi.HeaderFilterStrategy.Direction;
import org.apache.cxf.binding.soap.SoapBindingConstants;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.SoapVersionFactory;
import org.apache.cxf.headers.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MessageHeaderFilter} to drop all SOAP headers.
 */
public class SoapMessageHeaderFilter implements MessageHeaderFilter {
    private static final Logger LOG = LoggerFactory.getLogger(SoapMessageHeaderFilter.class);

    private static final List<String> ACTIVATION_NS = 
        Arrays.asList(SoapBindingConstants.SOAP11_BINDING_ID, 
                      SoapBindingFactory.SOAP_11_BINDING, 
                      SoapBindingFactory.SOAP_12_BINDING);
    
    @Override
    public List<String> getActivationNamespaces() {
        return ACTIVATION_NS;
    }

    @Override
    public void filter(Direction direction, List<Header> headers) {
        // Treat both in and out direction the same
        if (headers == null) {
            return;
        }
        
        Iterator<Header> iterator = headers.iterator();
        while (iterator.hasNext()) {
            Header header = iterator.next();
            LOG.trace("Processing header: {}", header);
            
            if (!(header instanceof SoapHeader)) {
                LOG.trace("Skipped header: {} since it is not a SoapHeader", header);
                continue;
            }
            
            SoapHeader soapHeader = SoapHeader.class.cast(header);
            for (Iterator<SoapVersion> itv = SoapVersionFactory.getInstance().getVersions(); itv.hasNext();) {
                SoapVersion version = itv.next();

                if (soapHeader.getActor() != null 
                    && soapHeader.getActor().equals(version.getNextRole())) {
                    // dropping headers if actor/role equals to {ns}/role|actor/next
                    // cxf SoapHeader needs to have soap:header@relay attribute, 
                    // then we can check for it here as well
                    LOG.trace("Filtered header: {}", header);
                    iterator.remove();
                    break;
                }
            }
        }
    }

}
