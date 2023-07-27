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
package org.apache.camel.component.zookeepermaster.group.internal;

import java.util.Arrays;

import org.apache.zookeeper.data.Stat;

public class ChildData<T> implements Comparable<ChildData<T>> {
    private final String path;
    private final Stat stat;
    private final byte[] data;
    private final T node;

    ChildData(String path, Stat stat, byte[] data, T node) {
        this.path = path;
        this.stat = stat;
        this.data = data;
        this.node = node;
    }

    /**
     * @inheritDoc
     *
     *             Note: this class has a natural ordering that is inconsistent with equals.
     */
    @Override
    public int compareTo(ChildData<T> rhs) {
        if (this == rhs) {
            return 0;
        }
        if (rhs == null || getClass() != rhs.getClass()) {
            return -1;
        }

        return path.compareTo(rhs.path);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        @SuppressWarnings("unchecked")
        ChildData<T> childData = (ChildData<T>) o;

        if (!Arrays.equals(data, childData.data)) {
            return false;
        }
        if (path != null ? !path.equals(childData.path) : childData.path != null) {
            return false;
        }
        if (stat != null ? !stat.equals(childData.stat) : childData.stat != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (stat != null ? stat.hashCode() : 0);
        result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
        return result;
    }

    /**
     * Returns the full path of the this child
     *
     * @return full path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the stat data for this child
     *
     * @return stat or null
     */
    public Stat getStat() {
        return stat;
    }

    /**
     * <p>
     * Returns the node data for this child when the cache mode is set to cache data.
     * </p>
     *
     * <p>
     * <b>NOTE:</b> the byte array returned is the raw reference of this instance's field. If you change the values in
     * the array any other callers to this method will see the change.
     * </p>
     *
     * @return node data or null
     */
    public byte[] getData() {
        return data;
    }

    /**
     * <p>
     * Returns the node for this group member.
     * </p>
     *
     * @return the node or null
     */
    public T getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "ChildData{path='" + path + '\'' + ", stat=" + stat + ", data=" + Arrays.toString(data) + '}';
    }
}
