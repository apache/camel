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
package org.apache.camel.non_wrapper;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// START SNIPPET: personProcessor
public class PersonProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(PersonProcessor.class);

    public void process(Exchange exchange) throws Exception {
        LOG.info("processing exchange in camel");

        BindingOperationInfo boi = (BindingOperationInfo)exchange.getProperty(BindingOperationInfo.class.getName());
        if (boi != null) {
            LOG.info("boi.isUnwrapped" + boi.isUnwrapped());
        }

        GetPerson person =  exchange.getIn().getBody(GetPerson.class);
        String personId = person.getPersonId();
        GetPersonResponse response = new GetPersonResponse();

        if (personId == null || personId.length() == 0) {
            LOG.info("person id 123, so throwing exception");
            // Try to throw out the soap fault message
            org.apache.camel.non_wrapper.types.UnknownPersonFault personFault =
                new org.apache.camel.non_wrapper.types.UnknownPersonFault();
            personFault.setPersonId("");
            org.apache.camel.non_wrapper.UnknownPersonFault fault =
                new org.apache.camel.non_wrapper.UnknownPersonFault("Get the null value of person name", personFault);
            // Since camel has its own exception handler framework, we can't throw the exception to trigger it
            // We just set the fault message in the exchange for camel-cxf component handling and return
            exchange.getOut().setFault(true);
            exchange.getOut().setBody(fault);
            return;
        }
        response.setPersonId(personId);
        response.setName("Bonjour");
        response.setSsn("123");
        LOG.info("setting Bonjour as the response");
        // Set the response message, first element is the return value of the operation,
        // the others are the holders of method parameters
        exchange.getOut().setBody(new Object[] {response});
    }

}
// END SNIPPET: personProcessor
