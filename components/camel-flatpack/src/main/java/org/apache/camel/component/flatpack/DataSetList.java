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
package org.apache.camel.component.flatpack;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;

import net.sf.flatpack.DataSet;

/**
 * @version
 */
public class DataSetList extends AbstractList<Map<String, Object>> {
    private final DataSet dataSet;

    public DataSetList(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public Map<String, Object> get(int index) {
        dataSet.absolute(index);
        return FlatpackConverter.toMap(dataSet);
    }

    public int size() {
        return dataSet.getRowCount();
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        dataSet.goTop();
        return new Iterator<Map<String, Object>>() {
            private boolean hasNext = dataSet.next();

            public boolean hasNext() {
                return hasNext;
            }

            public Map<String, Object> next() {
                // because of a limitation in split() we need to create an object for the current position
                // otherwise strangeness occurs when the same object is used to represent each row
                Map<String, Object> result = FlatpackConverter.toMap(dataSet);
                hasNext = dataSet.next();
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };
    }
}
