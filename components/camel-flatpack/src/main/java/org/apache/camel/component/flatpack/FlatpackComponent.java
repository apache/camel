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
package org.apache.camel.component.flatpack;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * A <a href="http://flatpack.sourceforge.net/">Flatpack Component</a>
 * for working with fixed width and delimited files
 *
 * @version 
 */
public class FlatpackComponent extends UriEndpointComponent {

    public static final String HEADER_ID = "header";
    public static final String TRAILER_ID = "trailer";

    public FlatpackComponent() {
        super(FlatpackEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean fixed = false;

        if (remaining.startsWith("fixed:")) {
            fixed = true;
            remaining = remaining.substring("fixed:".length());
        } else if (remaining.startsWith("delim:")) {
            remaining = remaining.substring("delim:".length());
        } else {
            // lets assume the rest of the string is just a name
            // to differentiate different named delimited endpoints
            remaining = "";
        }

        String resourceUri = remaining;
        FlatpackType type = fixed ? FlatpackType.fixed : FlatpackType.delim;

        FlatpackEndpoint answer = new FlatpackEndpoint(uri, this, resourceUri);
        answer.setType(type);
        setProperties(answer, parameters);
        return answer;
    }
}