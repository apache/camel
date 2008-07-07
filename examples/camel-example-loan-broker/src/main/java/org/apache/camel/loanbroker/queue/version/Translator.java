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
package org.apache.camel.loanbroker.queue.version;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

//START SNIPPET: translator
public class Translator implements Processor {

    public void process(Exchange exchange) throws Exception {
        String bank = (String)exchange.getIn().getHeader(Constants.PROPERTY_BANK);
        Double rate = (Double)exchange.getIn().getHeader(Constants.PROPERTY_RATE);
        String ssn = (String)exchange.getIn().getHeader(Constants.PROPERTY_SSN);
        exchange.getOut().setBody("Loan quotion for Client " + ssn + "."
                                  + " The lowest rate bank is " + bank + ", the rate is " + rate);
    }

}
//END SNIPPET: translator