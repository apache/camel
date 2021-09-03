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
package org.apache.camel.github;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;

@ResourceResolver("github")
public class GitHubResourceResolver extends ServiceSupport implements org.apache.camel.spi.ResourceResolver {

    // github:apache:camel:aws-ddb-streams-source.kamelet.yaml
    // https://raw.githubusercontent.com/apache/camel-kamelets/main/aws-ddb-streams-source.kamelet.yaml
    private static final String GITHUB_URL = "https://raw.githubusercontent.com/%s/%s/%s/%s";
    private static final String DEFAULT_BRANCH = "main";

    private CamelContext camelContext;

    private String branch = DEFAULT_BRANCH;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public String getSupportedScheme() {
        return "github";
    }

    @Override
    public Resource resolve(String location) {
        String[] parts = location.split(":");
        // scheme not in use as its github
        String org = null;
        String rep = null;
        String name = null;

        if (parts.length == 3) {
            org = parts[1];
            rep = parts[2];
            if (rep.contains("/")) {
                name = StringHelper.after(rep, "/");
                rep = StringHelper.before(rep, "/");
            }
        } else if (parts.length == 4) {
            org = parts[1];
            rep = parts[2];
            name = parts[3];
        } else if (parts.length == 5) {
            org = parts[1];
            rep = parts[2];
            branch = parts[3];
            name = parts[4];
        }
        if (org == null || rep == null || branch == null || name == null) {
            throw new IllegalArgumentException(location);
        }

        String target = String.format(GITHUB_URL, org, rep, branch, name);
        return new GitHubResource(camelContext, target);
    }
}
