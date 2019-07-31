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

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 * <code>ExistsOperation</code> is a basic ZooKeeper operation used to test the
 * existence of a given node.
 */
public class ExistsOperation extends ZooKeeperOperation<String> {

    private boolean mustExist;

    public ExistsOperation(ZooKeeper connection, String node) {
        this(connection, node, true);
    }

    public ExistsOperation(ZooKeeper connection, String node, boolean mustExist) {
        super(connection, node);
        this.mustExist = mustExist;
    }

    @Override
    public OperationResult<String> getResult() {
        try {
            Stat statistics = connection.exists(node, true);
            boolean ok = isOk(statistics);
            if (LOG.isTraceEnabled()) {
                LOG.trace(ok ? "node exists" : "node does not exist");
            }
            return new OperationResult<>(node, statistics, ok);
        } catch (Exception e) {
            return new OperationResult<>(e);
        }
    }

    private boolean isOk(Stat statistics) {
        boolean ok = false;
        if (mustExist) {
            ok = statistics != null;
        } else {
            ok = statistics == null;
        }
        return ok;
    }

}
