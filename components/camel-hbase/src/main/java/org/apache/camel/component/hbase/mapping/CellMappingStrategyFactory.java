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
package org.apache.camel.component.hbase.mapping;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CellMappingStrategyFactory {

    public static final String STRATEGY = "CamelMappingStrategy";
    public static final String STRATEGY_CLASS_NAME = "CamelMappingStrategyClassName";

    public static final String HEADER = "header";
    public static final String BODY = "body";

    private static final Logger LOG = LoggerFactory.getLogger(CellMappingStrategyFactory.class);
    private static final Map<String, CellMappingStrategy> DEFAULT_STRATEGIES = new HashMap<>();

    public CellMappingStrategyFactory() {
        DEFAULT_STRATEGIES.put(HEADER, new HeaderMappingStrategy());
        DEFAULT_STRATEGIES.put(BODY, new BodyMappingStrategy());
    }

    public CellMappingStrategy getStrategy(Message message) {
        CellMappingStrategy strategy = null;

        //Check if strategy has been explicitly set.
        if (message.getHeader(STRATEGY) != null) {
            strategy = DEFAULT_STRATEGIES.get(message.getHeader(STRATEGY, String.class));
        }

        if (strategy == null && message.getHeader(STRATEGY_CLASS_NAME) != null) {
            strategy = loadStrategyFromClassName(message.getHeader(STRATEGY_CLASS_NAME, String.class));
        }

        if (strategy != null) {
            return strategy;
        }

        //Fallback to the default strategy.
        return DEFAULT_STRATEGIES.get(HEADER);
    }

    private CellMappingStrategy loadStrategyFromClassName(String strategyClassName) {
        // TODO: We ought to use ClassResolver from CamelContext API
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try {
                Class<?> clazz = classLoader.loadClass(strategyClassName);
                return (CellMappingStrategy) clazz.newInstance();
            } catch (Throwable e) {
                LOG.warn("Failed to load HBase cell mapping strategy from class {}.", strategyClassName);
            }
        }
        return null;
    }
}
