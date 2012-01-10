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
package org.apache.camel.wsdl_first;

import javax.jws.WebService;
import javax.xml.ws.Holder;

@WebService(serviceName = "PersonService",
        targetNamespace = "http://camel.apache.org/wsdl-first",
        endpointInterface = "org.apache.camel.wsdl_first.Person")
public class PersonImpl implements Person {

    public void getPerson(Holder<String> personId, Holder<String> ssn,
            Holder<String> name) throws UnknownPersonFault {
        if (personId.value == null || personId.value.length() == 0) {
            org.apache.camel.wsdl_first.types.UnknownPersonFault
                fault = new org.apache.camel.wsdl_first.types.UnknownPersonFault();
            fault.setPersonId(personId.value);
            throw new UnknownPersonFault("Get the null value of person name", fault);
        }
        name.value = "Bonjour";
        ssn.value = "000-000-0000";
    }

}
