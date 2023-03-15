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
package org.apache.camel.tooling.maven;

import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

public class DefaultRepositoryResolver extends ServiceSupport implements RepositoryResolver {

    private Properties repos;

    @Override
    public String resolveRepository(String idOrUrl) {
        String answer = idOrUrl;
        if (repos != null) {
            answer = repos.getProperty(idOrUrl, idOrUrl);
        }
        return answer;
    }

    @Override
    protected void doBuild() throws Exception {
        InputStream is = RepositoryResolver.class.getClassLoader().getResourceAsStream("known-maven-repos.properties");
        if (is != null) {
            repos = new Properties();
            repos.load(is);
        }
        IOHelper.close(is);
    }
}
