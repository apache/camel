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
package org.apache.camel.component.github;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link GitHubEndpoint}.
 */
@Component("github")
public class GitHubComponent extends DefaultComponent {

    @Metadata(label = "security", secret = true)
    private String oauthToken;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GitHubEndpoint endpoint = new GitHubEndpoint(uri, this);
        endpoint.setOauthToken(oauthToken);

        setProperties(endpoint, parameters);

        String[] parts = remaining.split("/");
        if (parts.length >= 1) {
            String s = parts[0];
            GitHubType type = getCamelContext().getTypeConverter().convertTo(GitHubType.class, s);
            endpoint.setType(type);
            if (parts.length > 1) {
                s = parts[1];
                endpoint.setBranchName(s);
            }
        }
        return endpoint;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * GitHub OAuth token. Must be configured on either component or endpoint.
     */
    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

}
