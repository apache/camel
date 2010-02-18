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
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class HawtDBFile extends HawtPageFileFactory implements Service {

    private final static BTreeIndexFactory<String,Integer> indexesFactory = new BTreeIndexFactory<String,Integer>();
    private final static BTreeIndexFactory<Buffer,Buffer> indexFactory = new BTreeIndexFactory<Buffer,Buffer>();
    
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
        final boolean initialize = !file.exists();
        open();
        pageFile = getConcurrentPageFile();
        
        execute(new Work<Boolean>() {
            public Boolean execute(Transaction tx) {
                if( initialize ) {
                    int page = tx.allocator().alloc(1);
                    // if we just created the file, first allocated page should be 0
                    assert page == 0;
                    indexesFactory.create(tx, 0);
                    System.out.println("Aggregation repository data store created.");
                } else {
                    Index<String, Integer> indexes = indexesFactory.open(tx, 0);
                    System.out.println("You have "+indexes.size()+" aggregation repositories stored.");
                }
                return true;
            }
        });
    }

    public void stop() {
        close();
        pageFile = null;
    }
    
    public <T> T execute(Work<T> work) {
        Transaction tx = pageFile.tx();
        try {
            T rc = work.execute(tx);
            tx.commit();
            return rc;
        } catch (RuntimeException e){
            tx.rollback();
            throw e;
        }
    }

    public Index<Buffer, Buffer> getRepositoryIndex(Transaction tx, String name) {
        Index<String, Integer> indexes = indexesFactory.open(tx, 0);
        Integer location = indexes.get(name);
        if( location == null ) {
            // create it..
            return indexFactory.create(tx, tx.allocator().alloc(1));
        } else  {
            return indexFactory.open(tx, location);
        }
    }
    
}
