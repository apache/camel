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
package org.apache.camel.processor.async;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;

public class MyAsyncComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MyAsyncEndpoint answer = new MyAsyncEndpoint(uri, this);
        answer.setReply(prepareReply(remaining));
        setProperties(answer, parameters);
        return answer;
    }

    private String prepareReply(String value) {
        // to make URIs valid we make the conventions of using ':' for ' ' and
        // capitalize words
        String[] words = value.split(":");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(result.isEmpty() ? "" : " ");
            result.append(StringHelper.capitalize(word));
        }
        return result.toString();
    }
}
