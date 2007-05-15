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
package org.apache.camel.bam;

import org.apache.camel.util.Time;

/**
 * A temporal rule
 *
 * @version $Revision: $
 */
public class TemporalRule {
    private TimeExpression first;
    private TimeExpression second;
    private Time gap;

    public TemporalRule(TimeExpression left, TimeExpression right) {
        this.first = left;
        this.second = right;
    }

    /*
    public void process(Exchange exchange) {
        Time firstTime = evaluateTime(exchange);
        if (firstTime == null) {
            // TODO add test that if second happes first
            return;
        }
        Time secondTime = evaluateTime(exchange);
        if (secondTime == null) {
            // TODO add test that things have expired

        }
        else {
            if (secondTime.delta(firstTime.plus(gap)) > 0) {
                // TODO               
            }
        }
    }
    */

    public TemporalRule expectWithin(Time time) {
        return this;
    }

    public TemporalRule errorIfOver(Time time) {
        // TODO
        return this;
    }
}
