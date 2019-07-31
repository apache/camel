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
package org.apache.camel.component.zookeeper;

import org.apache.zookeeper.CreateMode;

/**
 * Nodes created with any of Sequential {@link CreateMode}s will have a 10
 * character sequence attached to their node names.
 * <code>SequenceComparator</code> is a Natural comparator used to compare lists
 * of objects with these appended sequences.
 */
public class SequenceComparator extends NaturalSortComparator {

    public static final int ZOOKEEPER_SEQUENCE_LENGTH = 10;

    @Override
    public int compare(CharSequence sequencedNode, CharSequence otherSequencedNode) {
        if (sequencedNode == null && otherSequencedNode == null) {
            return 0;
        }
        if (sequencedNode != null && otherSequencedNode == null) {
            return 1;
        }
        if (sequencedNode == null && otherSequencedNode != null) {
            return -1;
        }
        return super.compare(getZooKeeperSequenceNumber(sequencedNode), getZooKeeperSequenceNumber(otherSequencedNode));
    }

    private CharSequence getZooKeeperSequenceNumber(CharSequence sequencedNodeName) {
        int len = sequencedNodeName.length();
        return sequencedNodeName.subSequence(len - ZOOKEEPER_SEQUENCE_LENGTH, len);
    }
}
