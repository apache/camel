/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.utils.cassandra;

/**
 * Util class to help managing Cassandra primary key
 */
public class CassandraPKHelper {
    /**
     * Partition + cluster key column names
     */
    private String[] columns = new String[]{"ID", "KEY"};

    public CassandraPKHelper() {
    }

    public void appendColumns(StringBuilder cqlBuilder, String sep, int maxColumnIndex) {
        for (int i = 0; i < maxColumnIndex; i++) {
            if (i > 0) {
                cqlBuilder.append(sep);
            }
            cqlBuilder.append(columns[i]);
        }
    }

    public void appendColumns(StringBuilder cqlBuilder, String sep) {
        CassandraPKHelper.this.appendColumns(cqlBuilder, sep, columns.length);
    }

    public void appendWhere(StringBuilder cqlBuilder, int maxColumnIndex) {
        cqlBuilder.append(" where ");
        CassandraPKHelper.this.appendColumns(cqlBuilder, "=? and ", maxColumnIndex);
        cqlBuilder.append("=?");
    }
    public void appendWhere(StringBuilder cqlBuilder) {
        appendWhere(cqlBuilder, columns.length);
    }
    public void appendPlaceholders(StringBuilder cqlBuilder) {
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                cqlBuilder.append(",");
            }
            cqlBuilder.append("?");
        }
    }
    public String[] getColumns() {
        return columns;
    }

    public void setColumns(String ... columns) {
        this.columns = columns;
    }
}
