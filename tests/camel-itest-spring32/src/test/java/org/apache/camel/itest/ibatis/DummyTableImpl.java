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
package org.apache.camel.itest.ibatis;

import java.util.Collection;
import java.util.Iterator;

import org.springframework.orm.ibatis.support.SqlMapClientDaoSupport;

/**
 * @version 
 */
public class DummyTableImpl extends SqlMapClientDaoSupport implements DummyTable {

    public void add(int value) {
        getSqlMapClientTemplate().insert("table.add", value);
    }

    public void create() {
        getSqlMapClientTemplate().update("table.create");
    }

    public void clear() {
        getSqlMapClientTemplate().delete("table.clear");
    }

    public void drop() {
        getSqlMapClientTemplate().update("table.drop");
    }

    public Iterator<Integer> iterator() {
        return values().iterator();
    }

    @SuppressWarnings("unchecked")
    public Collection<Integer> values() {
        return getSqlMapClientTemplate().queryForList("table.values");
    }

}
