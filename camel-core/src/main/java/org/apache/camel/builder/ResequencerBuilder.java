/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.processor.Resequencer;

import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class ResequencerBuilder extends FromBuilder {
    private final List<Expression<Exchange>> expressions;
    private long batchTimeout = 1000L;
    private int batchSize = 100;

    public ResequencerBuilder(FromBuilder builder, List<Expression<Exchange>> expressions) {
        super(builder);
        this.expressions = expressions;
    }

    @Override
    public Route createRoute() throws Exception {
        final Processor processor = super.createProcessor();
        final Resequencer resequencer = new Resequencer(getFrom(), processor, expressions);

        return new Route<Exchange>(getFrom()) {
            protected void addServices(List<Service> list) throws Exception {
                list.add(resequencer);
            }

            @Override
            public String toString() {
                return "ResequencerRoute[" + getEndpoint() + " -> " + processor + "]";
            }
        };
    }

    // Builder methods
    //-------------------------------------------------------------------------
    public ResequencerBuilder batchSize(int batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    public ResequencerBuilder batchTimeout(int batchTimeout) {
        setBatchTimeout(batchTimeout);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }
}
