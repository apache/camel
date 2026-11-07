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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.spi.StepListener;

/**
 * A {@link StepListener} for tests that records each event as a line {@code "<event> <routeId>/<nodeId> [<value>]"},
 * and the threads of the callbacks. Its state is the {@code "<routeId>/<nodeId>"} of the call.
 */
class StepListenerRecorder implements StepListener {

    final Queue<String> events = new ConcurrentLinkedQueue<>();
    final Map<String, String> callbackThreads = new ConcurrentHashMap<>();
    final Map<String, String> returnThreads = new ConcurrentHashMap<>();

    @Override
    public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
        String state = routeId + "/" + nodeId;
        events.add("call " + state);
        return state;
    }

    @Override
    public void onStepReturn(Object state, boolean doneSync) {
        returnThreads.put((String) state, Thread.currentThread().getName());
        events.add("return " + state + " " + doneSync);
    }

    @Override
    public void onStepThrow(Object state, Throwable cause) {
        events.add("throw " + state + " " + cause.getMessage());
    }

    @Override
    public void onStepCallback(Object state, boolean doneSync) {
        callbackThreads.put((String) state, Thread.currentThread().getName());
        events.add("callback " + state + " " + doneSync);
    }

    /**
     * The events of the steps of one route, in order.
     */
    List<String> of(String routeId) {
        List<String> answer = new ArrayList<>();
        for (String event : events) {
            if (event.contains(" " + routeId + "/")) {
                answer.add(event);
            }
        }
        return answer;
    }

    /**
     * The channels of all the steps of a route, also the nested ones.
     */
    static List<Channel> channels(Route route) {
        List<Channel> answer = new ArrayList<>();
        collect(route.navigate(), answer);
        return answer;
    }

    private static void collect(Object node, List<Channel> answer) {
        if (node instanceof Channel channel) {
            answer.add(channel);
            collect(channel.getNextProcessor(), answer);
        } else if (node instanceof Navigate<?> navigate && navigate.hasNext()) {
            for (Object child : navigate.next()) {
                collect(child, answer);
            }
        }
    }

    /**
     * What the error handler of the channel calls for each attempt, or the channel itself when the step has no error
     * handler.
     */
    static Processor callOf(Channel channel) {
        Processor errorHandler = channel.getErrorHandler();
        if (errorHandler instanceof ErrorHandlerSupport support) {
            return support.getOutput();
        }
        return channel.getOutput();
    }
}
