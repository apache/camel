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
package org.apache.camel.bam;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Endpoint;

import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision: $
 */
public class BamBuilder {

    private List<Endpoint> endpoints = new ArrayList<Endpoint>();

    public void endpoint(Endpoint endpoint) {
        endpoints.add(endpoint);
    }

    public static BamBuilder monitor(RouteBuilder builder, String... uris) {
        BamBuilder answer = new BamBuilder();

        for (String uri : uris) {
            Endpoint endpoint = builder.endpoint(uri);
            answer.endpoint(endpoint);
        }
        return answer;
    }
}
