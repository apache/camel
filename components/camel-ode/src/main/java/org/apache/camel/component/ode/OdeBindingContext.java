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
package org.apache.camel.component.ode;

import javax.wsdl.PortType;
import javax.xml.namespace.QName;

import org.apache.ode.bpel.iapi.BindingContext;
import org.apache.ode.bpel.iapi.Endpoint;
import org.apache.ode.bpel.iapi.EndpointReference;
import org.apache.ode.bpel.iapi.PartnerRoleChannel;

/**
 * @version $Revision$
 */
public class OdeBindingContext implements BindingContext {

    public EndpointReference activateMyRoleEndpoint(QName processId, Endpoint roleEndpoint) {
        System.out.println("activateMyRoleEndpoint");
        return null;
    }

    public void deactivateMyRoleEndpoint(Endpoint endpoint) {
        System.out.println("deactivateMyRoleEndpoint");
    }

    public PartnerRoleChannel createPartnerRoleChannel(QName qName, PortType portType, Endpoint endpoint) {
        System.out.println("createPartnerRoleChannel");
        return null;
    }

    public long calculateSizeofService(EndpointReference endpointReference) {
        System.out.println("calculateSizeofService");
        return 0;
    }
}
