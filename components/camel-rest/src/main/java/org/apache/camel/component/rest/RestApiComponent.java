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
package org.apache.camel.component.rest;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Rest API component.
 */
@Component("rest-api")
public class RestApiComponent extends DefaultComponent {

    @Metadata(label = "consumer",
              description = "The name of the Camel component that serves the API documents, such as platform-http, servlet"
                            + " or jetty. The consumerComponentName option of an endpoint replaces it. If neither is set,"
                            + " the component of the rest configuration is used.")
    private String consumerComponentName;

    public RestApiComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RestApiEndpoint answer = new RestApiEndpoint(uri, this);
        answer.setConsumerComponentName(getConsumerComponentName());
        answer.setPath(remaining);

        setProperties(answer, parameters);
        answer.setParameters(parameters);

        // if no explicit component name was given, then fallback and use default configured component name
        if (answer.getConsumerComponentName() == null) {
            answer.setConsumerComponentName(getCamelContext().getRestConfiguration().getComponent());
        }

        return answer;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

}
