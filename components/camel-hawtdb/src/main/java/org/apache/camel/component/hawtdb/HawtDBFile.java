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
package org.apache.camel.component.hawtdb;

import org.apache.camel.Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.hawtdb.api.BTreeIndexFactory;
import org.fusesource.hawtdb.api.Index;
import org.fusesource.hawtdb.api.Transaction;
import org.fusesource.hawtdb.internal.page.HawtPageFile;
import org.fusesource.hawtdb.internal.page.HawtPageFileFactory;
import org.fusesource.hawtdb.util.buffer.Buffer;
import org.fusesource.hawtdb.util.marshaller.IntegerMarshaller;
import org.fusesource.hawtdb.util.marshaller.StringMarshaller;
import org.fusesource.hawtdb.util.marshaller.VariableBufferMarshaller;

/**
 * Manages access to a shared HawtDB file from multiple HawtDBAggregationRepository objects.
 */
public class HawtDBFile extends HawtPageFileFactory implements Service {

    private static final transient Log LOG = LogFactory.getLog(HawtDBFile.class);

    private final static BTreeIndexFactory<String, Integer> indexesFactory = new BTreeIndexFactory<String, Integer>();
    private final static BTreeIndexFactory<Buffer, Buffer> indexFactory = new BTreeIndexFactory<Buffer, Buffer>();

    public HawtDBFile() {
        setSync(false);
    }

    static {
        indexesFactory.setKeyMarshaller(StringMarshaller.INSTANCE);
        indexesFactory.setValueMarshaller(IntegerMarshaller.INSTANCE);
        indexesFactory.setDeferredEncoding(true);
        indexFactory.setKeyMarshaller(VariableBufferMarshaller.INSTANCE);
        indexFactory.setValueMarshaller(VariableBufferMarshaller.INSTANCE);
        indexFactory.setDeferredEncoding(true);
    }

    private HawtPageFile pageFile;

    public void start() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting HawtDB using file: " + getFile());
        }

        final boolean initialize = !file.exists();
        open();
        pageFile = getConcurrentPageFile();

        execute(new Work<Boolean>() {
            public Boolean execute(Transaction tx) {
                if (initialize) {
                    int page = tx.allocator().alloc(1);
                    // if we just created the file, first allocated page should be 0
                    assert page == 0;
                    indexesFactory.create(tx, 0);
                    LOG.info("Aggregation repository data store created using file: " + getFile());
                } else {
                    Index<String, Integer> indexes = indexesFactory.open(tx, 0);
                    LOG.info("Aggregation repository data store loaded using file: " + getFile()
                            + " containing " + indexes.size() + " repositories.");
                }
                return true;
            }
        });
    }

    public void stop() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping HawtDB using file: " + getFile());
        }

        close();
        pageFile = null;
    }

    public <T> T execute(Work<T> work) {
        Transaction tx = pageFile.tx();
        try {
            T rc = work.execute(tx);
            tx.commit();
            return rc;
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        }
    }

    public Index<Buffer, Buffer> getRepositoryIndex(Transaction tx, String name) {
        Index<String, Integer> indexes = indexesFactory.open(tx, 0);
        Integer location = indexes.get(name);
        if (location == null) {
            // create it..
            int page = tx.allocator().alloc(1);
            Index<Buffer, Buffer> created = indexFactory.create(tx, page);

            // add it to indexes so we can find it the next time
            indexes.put(name, page);

            return created;
        } else {
            return indexFactory.open(tx, location);
        }
    }

}
