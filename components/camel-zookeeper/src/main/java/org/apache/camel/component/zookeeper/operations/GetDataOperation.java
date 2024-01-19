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

import static java.lang.String.format;

/**
 * <code>GetDataOperation</code> is a basic operation to immediately retrieve the data associated with a given ZooKeeper
 * node.
 */
public class GetDataOperation extends ZooKeeperOperation<byte[]> {

    public GetDataOperation(ZooKeeper connection, String node) {
        super(connection, node);
    }

    @Override
    public OperationResult<byte[]> getResult() {
        try {
            Stat statistics = new Stat();

            if (LOG.isDebugEnabled()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace(format("Received data from '%s' path with statistics '%s'", node, statistics));
                } else {
                    LOG.debug(format("Received data from '%s' path ", node));
                }
            }
            return new OperationResult<>(connection.getData(node, true, statistics), statistics);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OperationResult<>(e);
        } catch (Exception e) {
            return new OperationResult<>(e);
        }
    }

}
