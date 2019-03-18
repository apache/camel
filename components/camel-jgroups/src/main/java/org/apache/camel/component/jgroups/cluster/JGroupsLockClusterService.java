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
package org.apache.camel.component.jgroups.cluster;

import org.apache.camel.support.cluster.AbstractCamelClusterService;

public class JGroupsLockClusterService extends AbstractCamelClusterService<JGroupsLockClusterView> {

    private static final String DEFAULT_JGROUPS_CONFIG = "locking.xml";
    private static final String DEFAULT_JGROUPS_CLUSTERNAME = "jgroups-master";

    private String jgroupsConfig;
    private String jgroupsClusterName;

    public JGroupsLockClusterService() {
        this.jgroupsConfig = DEFAULT_JGROUPS_CONFIG;
        this.jgroupsClusterName = DEFAULT_JGROUPS_CLUSTERNAME;
    }
    public JGroupsLockClusterService(String jgroupsConfig, String jgroupsClusterName) {
        this.jgroupsConfig = jgroupsConfig;
        this.jgroupsClusterName = jgroupsClusterName;
    }

    @Override
    protected JGroupsLockClusterView createView(String namespace) throws Exception {
        return new JGroupsLockClusterView(this, namespace, jgroupsConfig, jgroupsClusterName);
    }

    public String getJgroupsConfig() {
        return jgroupsConfig;
    }

    public void setJgroupsConfig(String jgroupsConfig) {
        this.jgroupsConfig = jgroupsConfig;
    }

    public String getJgroupsClusterName() {
        return jgroupsClusterName;
    }

    public void setJgroupsClusterName(String jgroupsClusterName) {
        this.jgroupsClusterName = jgroupsClusterName;
    }
}
