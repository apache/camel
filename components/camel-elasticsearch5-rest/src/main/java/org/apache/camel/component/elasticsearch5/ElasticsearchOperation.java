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
package org.apache.camel.component.elasticsearch5;

/**
 * The ElasticSearch server operations list which are implemented
 * 
 * Index        - Index a document associated with a given index and type
 * Update       - Updates a document based on a script
 * Bulk         - Executes a bulk of index / delete operations
 * BulkIndex   - Executes a bulk of index / delete operations
 * GetById    - Gets the document that was indexed from an index with a type and id
 * MultiGet     - Multiple get documents
 * Delete       - Deletes a document from the index based on the index, type and id
 * Search       - Search across one or more indices and one or more types with a query
 * Exists       - Checks the index exists or not (using search with size=0 and terminate_after=1 parameters)
 * 
 */
public enum ElasticsearchOperation {
    Index("Index"),
    Update("Update"),
    Bulk("Bulk"),
    BulkIndex("BulkIndex"),
    GetById("GetById"),
    MultiGet("MultiGet"),
    Delete("Delete"),
    DeleteIndex("DeleteIndex"),
    Search("Search"),
    Exists("Exists"),
    Ping("Ping");

    private final String text;

    ElasticsearchOperation(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
