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
package org.apache.camel.component.jgroups.raft.cluster;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.jgroups.raft.RaftHandle;

@Metadata(label = "bean",
          description = "JGroups Raft based cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public class JGroupsRaftClusterService extends AbstractCamelClusterService<JGroupsRaftClusterView> {

    private static final String DEFAULT_JGROUPS_CONFIG = "raft.xml";
    private static final String DEFAULT_JGROUPS_CLUSTERNAME = "jgroupsraft-master";

    @Metadata(description = "Unique Raft id", required = true)
    private String id;
    @Metadata(description = "The path to the JGroups Raft configuration", defaultValue = DEFAULT_JGROUPS_CONFIG)
    private String jgroupsConfig;
    @Metadata(description = "The name of the cluster", defaultValue = DEFAULT_JGROUPS_CLUSTERNAME)
    private String jgroupsClusterName;
    @Metadata(label = "advanced", description = "To use a custom RaftHandler")
    private RaftHandle raftHandle;

    public JGroupsRaftClusterService() {
        this.jgroupsConfig = DEFAULT_JGROUPS_CONFIG;
        this.jgroupsClusterName = DEFAULT_JGROUPS_CLUSTERNAME;
    }

    public JGroupsRaftClusterService(String jgroupsConfig, String jgroupsClusterName, RaftHandle raftHandle, String raftId) {
        this.jgroupsConfig = jgroupsConfig;
        this.jgroupsClusterName = jgroupsClusterName;
        this.raftHandle = raftHandle;
        setId(raftId);
    }

    @Override
    protected JGroupsRaftClusterView createView(String namespace) throws Exception {
        return new JGroupsRaftClusterView(this, namespace, jgroupsConfig, jgroupsClusterName, raftHandle, getId());
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        this.id = id;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public String getRaftId() {
        return getId();
    }

    public void setRaftId(String raftId) {
        setId(raftId);
    }

    public RaftHandle getRaftHandle() {
        return raftHandle;
    }

    public void setRaftHandle(RaftHandle raftHandle) {
        this.raftHandle = raftHandle;
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
