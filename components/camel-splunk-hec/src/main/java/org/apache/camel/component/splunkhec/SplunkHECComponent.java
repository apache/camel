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
package org.apache.camel.component.splunkhec;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.URISupport;

@Component("splunk-hec")
public class SplunkHECComponent extends DefaultComponent {

    public SplunkHECComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SplunkHECEndpoint answer = new SplunkHECEndpoint(uri, this, new SplunkHECConfiguration());
        setProperties(answer, parameters);

        String[] remainingSplits = remaining.split("/", 2);
        if (remainingSplits.length != 2) {
            throw new IllegalArgumentException("Invalid URI: " + URISupport.sanitizeUri(uri));
        }

        if (answer.getSplunkURL() == null || answer.getToken() == null) {
            if (answer.getSplunkURL() == null) {
                answer.setSplunkURL(remainingSplits[0]);
            }

            if (answer.getToken() == null) {
                answer.setToken(remainingSplits[1]);
            }
        }

        return answer;
    }
}
