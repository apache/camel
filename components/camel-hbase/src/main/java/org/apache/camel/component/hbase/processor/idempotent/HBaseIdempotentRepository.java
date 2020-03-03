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
package org.apache.camel.component.hbase.processor.idempotent;

import java.io.IOException;

import org.apache.camel.component.hbase.HBaseHelper;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseIdempotentRepository.class);

    private final String tableName;
    private final String family;
    private final String qualifier;
    private final Configuration configuration;
    private Connection connection;
    private Table table;

    public HBaseIdempotentRepository(Configuration configuration, String tableName, String family, String qualifier) throws IOException {
        this.tableName = tableName;
        this.family = family;
        this.qualifier = qualifier;
        this.configuration = configuration;
        this.connection = null;
        this.table = null;
    }

    @Override
    public boolean add(String o) {
        try {
            if (contains(o)) {
                return false;
            }
            byte[] b = HBaseHelper.toBytes(o);
            Put put = new Put(b);
            put.addColumn(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(qualifier), b);
            table.put(put);
            return true;
        } catch (Exception e) {
            LOG.warn("Error adding object {} to HBase repository.", o);
            return false;
        }
    }

    @Override
    public boolean contains(String o) {
        try {
            byte[] b = HBaseHelper.toBytes(o);
            Get get = new Get(b);
            get.addColumn(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(qualifier));
            return table.exists(get);
        } catch (Exception e) {
            LOG.warn("Error reading object {} from HBase repository.", o);
            return false;
        }
    }

    @Override
    public boolean remove(String o) {
        try {
            byte[] b = HBaseHelper.toBytes(o);
            if (table.exists(new Get(b))) {
                Delete delete = new Delete(b);
                table.delete(delete);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            LOG.warn("Error removing object {} from HBase repository.", o);
            return false;
        }
    }

    @Override
    public boolean confirm(String o) {
        return true;
    }
    
    @Override
    public void clear() {
        Scan s = new Scan();
        ResultScanner scanner;
        try {
            scanner = table.getScanner(s);
            for (Result rr : scanner) {
                Delete d = new Delete(rr.getRow());
                table.delete(d);
            } 
        } catch (Exception e) {
            LOG.warn("Error clear HBase repository {}", table);
        }
    }    

    @Override
    protected void doStart() throws Exception {
        this.connection = ConnectionFactory.createConnection(configuration);
        this.table = this.connection.getTable(TableName.valueOf(tableName));
    }

    @Override
    protected void doStop() throws Exception {
        if (table != null) {
            table.close();
        }

        if (connection != null) {
            connection.close();
        }
    }
}
