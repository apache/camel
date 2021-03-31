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
package org.apache.camel.main;

import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To eager load a set of classes that Camel always uses.
 *
 * This is for optimization purposes to ensure the classes are loaded before Camel is started and would load the classes
 * while processing the first messages.
 */
public final class EagerClassloadedHelper {

    public static final Logger LOG = LoggerFactory.getLogger(EagerClassloadedHelper.class);

    private EagerClassloadedHelper() {
    }

    public static void eagerLoadClasses() {
        StopWatch watch = new StopWatch();

        int count = 0;
        // EAGER-CLASSLOADED: START
        count = 9;
        org.apache.camel.impl.engine.CamelInternalProcessor.onClassloaded(LOG);
        org.apache.camel.impl.engine.DefaultReactiveExecutor.onClassloaded(LOG);
        org.apache.camel.impl.engine.DefaultUnitOfWork.onClassloaded(LOG);
        org.apache.camel.processor.Pipeline.onClassloaded(LOG);
        org.apache.camel.processor.PipelineHelper.onClassloaded(LOG);
        org.apache.camel.processor.errorhandler.DefaultErrorHandler.onClassloaded(LOG);
        org.apache.camel.support.ExchangeHelper.onClassloaded(LOG);
        org.apache.camel.support.MessageHelper.onClassloaded(LOG);
        org.apache.camel.support.UnitOfWorkHelper.onClassloaded(LOG);
        // EAGER-CLASSLOADED: END

        String time = TimeUtils.printDuration(watch.taken());
        LOG.info("Eager loaded {} classes in {}", count, time);
    }
}
