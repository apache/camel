/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.flatpack;

import net.sf.flatpack.DataSet;

import java.util.AbstractList;
import java.util.Iterator;

/**
 * @version $Revision: 1.1 $
 */
public class DataSetList extends AbstractList {
    private final DataSet dataSet;

    public DataSetList(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public Object get(int index) {
        Iterator iter = iterator();
        for (int i = 0; iter.hasNext(); i++) {
            Object value = iter.next();
            if (i == index) {
                return value;
            }
        }
        return null;
    }

    public int size() {
        int answer = 0;
        for (Iterator iter = iterator(); iter.hasNext(); ) {
            iter.next();
            answer++;
        }
        return answer;
    }

    @Override
    public Iterator iterator() {
        dataSet.goTop();
        return new Iterator() {
            public boolean hasNext() {
                return dataSet.next();
            }

            public Object next() {
                // TODO because of a limitation in split()
                // we need to create an object for the current position
                // otherwise strangeness occurs when the same object is used to represent
                // each row
                return FlatpackConverter.toMap(dataSet);
            }

            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };

    }
}
