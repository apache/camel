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
package org.apache.camel.component.hbase.processor.idempotent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.apache.camel.component.hbase.HBaseHelper;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseIdempotentRepository extends ServiceSupport implements IdempotentRepository<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseIdempotentRepository.class);

    private final String tableName;
    private final String family;
    private final String qualifer;
    private final HTable table;

    public HBaseIdempotentRepository(Configuration configuration, String tableName, String family, String qualifier) throws IOException {
        this.tableName = tableName;
        this.family = family;
        this.qualifer = qualifier;
        //In the case of idempotent repository we do not want to catch exceptions related to HTable.
        this.table = new HTable(configuration, tableName);
    }

    @Override
    public boolean add(Object o) {
        try {
            synchronized (tableName.intern()) {
                if (contains(o)) {
                    return false;
                }
                byte[] b = toBytes(o);
                Put put = new Put(b);
                put.add(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(qualifer), b);
                table.put(put);
                table.flushCommits();
                return true;
            }
        } catch (Exception e) {
            LOG.warn("Error adding object {} to HBase repository.", o);
            return false;
        }
    }

    @Override
    public boolean contains(Object o) {
        try {
            byte[] b = toBytes(o);
            Get get = new Get(b);
            get.addColumn(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(qualifer));
            return table.exists(get);
        } catch (Exception e) {
            LOG.warn("Error reading object {} from HBase repository.", o);
            return false;
        }
    }

    @Override
    public boolean remove(Object o) {
        try {
            byte[] b = toBytes(o);
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
    public boolean confirm(Object o) {
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
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    private byte[] toBytes(Object obj) {
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else if (obj instanceof Byte) {
            return Bytes.toBytes((Byte) obj);
        } else if (obj instanceof Short) {
            return Bytes.toBytes((Short) obj);
        } else if (obj instanceof Integer) {
            return Bytes.toBytes((Integer) obj);
        }  else if (obj instanceof Long) {
            return Bytes.toBytes((Long) obj);
        }  else if (obj instanceof Double) {
            return Bytes.toBytes((Double) obj);
        }  else if (obj instanceof String) {
            return Bytes.toBytes((String) obj);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(baos);
                oos.writeObject(obj);
                return  baos.toByteArray();
            } catch (IOException e) {
                LOG.warn("Error while serializing object. Null will be used.", e);
                return null;
            } finally {
                IOHelper.close(oos);
                IOHelper.close(baos);
            }
        }
    }
}
