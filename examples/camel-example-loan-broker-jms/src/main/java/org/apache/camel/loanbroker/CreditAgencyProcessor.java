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
package org.apache.camel.loanbroker;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

//START SNIPPET: creditAgency
public class CreditAgencyProcessor implements Processor {

    public void process(Exchange exchange) throws Exception {
        String ssn = exchange.getIn().getHeader(Constants.PROPERTY_SSN, String.class);
        int score = (int) (Math.random() * 600 + 300);
        int hlength = (int) (Math.random() * 19 + 1);

        exchange.getOut().setHeader(Constants.PROPERTY_SCORE, score);
        exchange.getOut().setHeader(Constants.PROPERTY_HISTORYLENGTH, hlength);
        exchange.getOut().setHeader(Constants.PROPERTY_SSN, ssn);
    }

}
//END SNIPPET: creditAgency
