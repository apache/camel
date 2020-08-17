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
package org.apache.camel.model;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper for {@link org.apache.camel.model.RestContextRefDefinition}.
 */
public final class RestContextRefDefinitionHelper {

    private RestContextRefDefinitionHelper() {
    }

    /**
     * Lookup the rests from the {@link org.apache.camel.model.RestContextRefDefinition}.
     *
     * @param  camelContext the CamelContext
     * @param  ref          the id of the {@link org.apache.camel.model.RestContextRefDefinition} to lookup and get the
     *                      routes.
     * @return              the rests.
     */
    public static List<RestDefinition> lookupRests(CamelContext camelContext, String ref) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(ref, "ref");

        List<RestDefinition> answer = CamelContextHelper.lookup(camelContext, ref, List.class);
        if (answer == null) {
            throw new IllegalArgumentException("Cannot find RestContext with id " + ref);
        }
        return answer;
    }

}
