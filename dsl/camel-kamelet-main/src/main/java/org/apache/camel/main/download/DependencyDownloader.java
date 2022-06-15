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
package org.apache.camel.main.download;

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;

/**
 * To download dependencies at runtime.
 */
public interface DependencyDownloader extends CamelContextAware, StaticService {

    String getRepos();

    /**
     * Additional maven repositories for download on-demand (Use commas to separate multiple repositories).
     */
    void setRepos(String repos);

    boolean isFresh();

    /**
     * Make sure we use fresh (i.e. non-cached) resources.
     */
    void setFresh(boolean fresh);

    /**
     * Downloads the dependency
     *
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     */
    void downloadDependency(String groupId, String artifactId, String version);

}
