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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//START SNIPPET: creditAgency
public class CreditAgency implements Processor {
    private static final transient Log LOG = LogFactory.getLog(CreditAgency.class);

    public void process(Exchange exchange) throws Exception {
        LOG.info("Receiving credit agency request");
        String ssn = exchange.getIn().getHeader(Constants.PROPERTY_SSN, String.class);
        int score = (int) (Math.random() * 600 + 300);
        int hlength = (int) (Math.random() * 19 + 1);
        exchange.getOut().setHeader(Constants.PROPERTY_SCORE, new Integer(score));
        exchange.getOut().setHeader(Constants.PROPERTY_HISTORYLENGTH, new Integer(hlength));
        exchange.getOut().setHeader(Constants.PROPERTY_SSN, ssn);
    }

}
//END SNIPPET: creditAgency