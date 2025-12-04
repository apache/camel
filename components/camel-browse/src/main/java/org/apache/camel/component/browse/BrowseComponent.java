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

package org.apache.camel.component.browse;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("browse")
public class BrowseComponent extends DefaultComponent {

    @Metadata(
            label = "advanced",
            description = "Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.")
    private int browseLimit;

    public BrowseComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BrowseEndpoint endpoint = new BrowseEndpoint(uri, this);
        endpoint.setName(remaining);
        endpoint.setBrowseLimit(browseLimit);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public int getBrowseLimit() {
        return browseLimit;
    }

    public void setBrowseLimit(int browseLimit) {
        this.browseLimit = browseLimit;
    }
}
