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
package org.apache.camel.component.zookeeper.operations;

import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import static java.lang.String.format;

/**
 * <code>CreateOperation</code> is a basic Zookeeper operation used to create and set the data contained in a given node
 */
public class CreateOperation extends ZooKeeperOperation<String> {

    private static final List<ACL> DEFAULT_PERMISSIONS = Ids.OPEN_ACL_UNSAFE;

    private static final CreateMode DEFAULT_MODE = CreateMode.EPHEMERAL;

    private byte[] data;

    private List<ACL> permissions = DEFAULT_PERMISSIONS;

    private CreateMode createMode = DEFAULT_MODE;

    public CreateOperation(ZooKeeper connection, String node) {
        super(connection, node);
    }

    @Override
    public OperationResult<String> getResult() {
        try {
            // ensure parent nodes is created first as persistent (cannot be ephemeral without children)
            ZooKeeperHelper.mkdirs(connection, node, false, CreateMode.PERSISTENT);
            String created = connection.create(node, data, permissions, createMode);
            if (LOG.isDebugEnabled()) {
                LOG.debug(format("Created node '%s' using mode '%s'", created, createMode));
            }
            // for consistency with other operations return an empty stats set.
            return new OperationResult<>(created, new Stat());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OperationResult<>(e);
        } catch (Exception e) {
            return new OperationResult<>(e);
        }
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setPermissions(List<ACL> permissions) {
        this.permissions = permissions;
    }

    public void setCreateMode(CreateMode createMode) {
        this.createMode = createMode;
    }

}
