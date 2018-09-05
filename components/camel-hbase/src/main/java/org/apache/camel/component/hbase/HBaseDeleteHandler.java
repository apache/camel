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
package org.apache.camel.component.hbase;

import java.io.IOException;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseDeleteHandler implements HBaseRemoveHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseDeleteHandler.class);

    /**
     * Performs a {@link Delete} of the specified row.
     */
    @Override
    public void remove(Table table, byte[] row) {
        Delete delete = new Delete(row);
        try {
            table.delete(delete);
        } catch (IOException e) {
            LOG.warn("Failed to delete row from table. This exception will be ignored.", e);
        }
    }
}
