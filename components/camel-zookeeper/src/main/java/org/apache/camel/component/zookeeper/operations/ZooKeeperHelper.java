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

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.common.PathUtils;

public final class ZooKeeperHelper {

    private ZooKeeperHelper() {
    }

    public static void mkdirs(ZooKeeper zookeeper, String path, boolean makeLastNode, CreateMode createMode) throws Exception {
        PathUtils.validatePath(path);

        int pos = 1; // skip first slash, root is guaranteed to exist
        do {
            pos = path.indexOf("/", pos + 1);

            if (pos == -1) {
                if (makeLastNode) {
                    pos = path.length();
                } else {
                    break;
                }
            }

            String subPath = path.substring(0, pos);
            if (zookeeper.exists(subPath, false) == null) {
                try {
                    zookeeper.create(subPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
                } catch (KeeperException.NodeExistsException e) {
                    // ignore... someone else has created it since we checked
                }
            }

        }
        while (pos < path.length());
    }

}
