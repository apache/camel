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
package org.apache.camel.component.interactivebrokers;

import java.util.ArrayList;

import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IConnectionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveBrokersBinding implements IConnectionHandler {

    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersBinding.class);
    private ApiController api;
    private volatile boolean ready;

    public InteractiveBrokersBinding(InteractiveBrokersTransportTuple tuple) {
        logger.info("New binding instantiated, trying to connect to {}", tuple);
        ILogger logWrapper = new ILogger() {
            @Override
            public void log(String message) {
                // FIXME - do all messages have the same severity?
                logger.trace(message);
            }
        };
        api = new ApiController(this, logWrapper, logWrapper);
        api.connect(tuple.getHost(), tuple.getPort(), tuple.getClientId(), null);

        // FIXME - should we wait here or in doStart() ?
        // logger.trace("waiting for connect...");
        // ensureConnected();
    }

    public void ensureConnected() {
        while (!ready) {
            Thread.yield();
        }
    }

    public ApiController getApiController() {
        return api;
    }

    @Override
    public void connected() {
        logger.info("API connected to TWS");
        ready = true;
    }

    @Override
    public void disconnected() {
        // TODO Auto-generated method stub
        // FIXME - how to reconnect?  restart market data?
    }

    @Override
    public void accountList(ArrayList<String> list) {
        // TODO Auto-generated method stub
    }

    @Override
    public void error(Exception e) {
        logger.error("Got exception notification", e);
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        logger.error("Got message id = {} errorCode = {} msg = {}",
            new Object[] {id, errorCode, errorMsg});
    }

    @Override
    public void show(String string) {
        logger.info("message for user: {}", string);
    }
}