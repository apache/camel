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
package org.apache.camel.component.atomix.ha;

import io.atomix.group.DistributedGroup;
import io.atomix.group.GroupMember;
import org.apache.camel.ha.CamelClusterMember;

class AtomixClusterMember<M extends GroupMember> implements CamelClusterMember {
    private final DistributedGroup group;
    private final M member;

    AtomixClusterMember(DistributedGroup group, M member) {
        this.group = group;
        this.member = member;
    }

    @Override
    public String getId() {
        return member.id();
    }

    @Override
    public boolean isMaster() {
        return group.election().term().leader().equals(member);
    }

    M getGroupMember() {
      return member;
    }
}
