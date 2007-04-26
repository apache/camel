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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;

/**
 * @version $Revision$
 */
public class ChoiceBuilder extends FromBuilder {

    private final FromBuilder parent;
    private List<WhenBuilder> predicateBuilders = new ArrayList<WhenBuilder>();
    private FromBuilder otherwise;

    public ChoiceBuilder(FromBuilder parent) {
        super(parent);
        this.parent = parent;
    }

    /**
     * Adds a predicate which if it is true then the message exchange is sent to the given destination
     *
     * @return a builder for creating a when predicate clause and action
     */
    @Fluent(nestedActions=true)
    public WhenBuilder when(
    		@FluentArg(value="predicate",element=true) 
    		Predicate predicate) {
        WhenBuilder answer = new WhenBuilder(this, predicate);
        predicateBuilders.add(answer);
        return answer;
    }

    @Fluent(nestedActions=true)
    public FromBuilder otherwise() {
        this.otherwise = new FromBuilder(parent);
        return otherwise;
    }

    public List<WhenBuilder> getPredicateBuilders() {
        return predicateBuilders;
    }

    public FromBuilder getOtherwise() {
        return otherwise;
    }

    @Override
    public Processor createProcessor() throws Exception {
        List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
        for (WhenBuilder predicateBuilder : predicateBuilders) {
            filters.add(predicateBuilder.createProcessor());
        }
        Processor otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = otherwise.createProcessor();
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }
}
