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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.Logger;

/**
 * Conditionally throws exception causing a rollback
 *
 * @author Kevin Ross
 */
public class ConditionalExceptionProcessor implements Processor {

    private Logger log = Logger.getLogger(getClass());
    private int count;

    public void process(Exchange exchange) throws Exception {

        setCount(getCount() + 1);

        AbstractTransactionTest
            .assertTrue(
                        "Expected only 2 calls to process() but encountered "
                            + getCount()
                            + ".  There should be 1 for intentionally triggered rollback, and 1 for the redelivery.",
                        getCount() <= 2);

        // should be printed 2 times due to one re-delivery after one failure
        log.info("Exchange[" + getCount() + "][" + ((getCount() <= 1) ? "Should rollback" : "Should succeed")
                 + "] = " + exchange);

        // force rollback on the second attempt
        if (getCount() <= 1) {
            throw new Exception("Rollback should be intentionally triggered: count[" + getCount() + "].");
        }
    }

    private void setCount(int count) {

        this.count = count;
    }

    public int getCount() {

        return count;
    }
}
