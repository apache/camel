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
package org.apache.camel.component.bean;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ParameterMappingStrategyHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ParameterMappingStrategyHelper.class);

    private ParameterMappingStrategyHelper() {
    }

    public static ParameterMappingStrategy createParameterMappingStrategy(CamelContext camelContext) {
        ParameterMappingStrategy answer;

        // lookup in registry first if there is a user define strategy
        Registry registry = camelContext.getRegistry();
        Set<ParameterMappingStrategy> set = registry.findByType(ParameterMappingStrategy.class);
        if (set.size() == 1) {
            answer = set.iterator().next();
        } else if (set.size() > 1) {
            LOG.warn(
                    "Found {} beans of type {} in registry. Only one custom instance is supported. Will use DefaultParameterMappingStrategy.",
                    set.size(), ParameterMappingStrategy.class.getName());
            answer = DefaultParameterMappingStrategy.INSTANCE;
        } else {
            // no then use the default one
            answer = DefaultParameterMappingStrategy.INSTANCE;
        }

        return answer;
    }

}
