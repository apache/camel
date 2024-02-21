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
package org.apache.camel.component.opensearch;

/**
 * The OpenSearch server operations list which are implemented
 * <p>
 * <ul>
 * <li>Index - Index a document associated with a given index</li>
 * <li>Update - Updates a document based on a script</li>
 * <li>Bulk - Executes a bulk of index / create/ update delete operations</li>
 * <li>GetById - Get an indexed document from its id</li>
 * <li>MultiGet - Multiple get documents</li>
 * <li>Delete - Deletes a document from the index based on the index and id</li>
 * <li>DeleteIndex - Deletes an index based on the index name</li>
 * <li>MultiSearch - Multiple Search across one or more indices with a query</li>
 * <li>Search - Search across one or more indices with a query</li>
 * <li>Exists - Checks whether the index exists or not</li>
 * <li>Ping - Pings the Opensearch cluster</li>
 * </ul>
 *
 *
 *
 *
 * (using search with size=0 and terminate_after=1 parameters)
 */
public enum OpensearchOperation {
    Index("Index"),
    Update("Update"),
    Bulk("Bulk"),
    GetById("GetById"),
    MultiGet("MultiGet"),
    MultiSearch("MultiSearch"),
    Delete("Delete"),
    DeleteIndex("DeleteIndex"),
    Search("Search"),
    Exists("Exists"),
    Ping("Ping");

    private final String text;

    OpensearchOperation(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
