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
package org.apache.camel.component.cxf;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;


/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">SOAP Component</a>
 *
 * @version $Revision$
 */
public class CxfSoapComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Map soapProps = IntrospectionSupport.extractProperties(parameters, "soap.");
        if (parameters.size() > 0) {
            remaining += "?" + URISupport.createQueryString(parameters);
        }
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(getCamelContext(), remaining);
        CxfSoapEndpoint soapEndpoint = new CxfSoapEndpoint(endpoint);
        setProperties(soapEndpoint, soapProps);
        soapEndpoint.init();
        return soapEndpoint;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }

}
