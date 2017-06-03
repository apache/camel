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
package org.apache.camel.component.hbase.model;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "row")
public class HBaseRow implements Cloneable {

    private Object id;
    //The row type can be optionally specified for Gets and Scan, to specify how the byte[] read will be converted.
    private Class<?> rowType = String.class;
    private Set<HBaseCell> cells;

    private long timestamp;

    public HBaseRow() {
        this(new LinkedHashSet<HBaseCell>());
    }

    public HBaseRow(Set<HBaseCell> cells) {
        this.cells = cells;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @XmlAttribute(name = "type")
    public Class<?> getRowType() {
        return rowType;
    }

    public void setRowType(Class<?> rowType) {
        this.rowType = rowType;
    }

    public Set<HBaseCell> getCells() {
        return cells;
    }

    public void setCells(Set<HBaseCell> cells) {
        this.cells = cells;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void apply(HBaseRow modelRow) {
        if (modelRow != null) {
            if (rowType == null && modelRow.getRowType() != null) {
                rowType = modelRow.getRowType();
            }
            for (HBaseCell modelCell : modelRow.getCells()) {
                if (!getCells().contains(modelCell)) {
                    HBaseCell cell = new HBaseCell();
                    cell.setFamily(modelCell.getFamily());
                    cell.setQualifier(modelCell.getQualifier());
                    cell.setValueType(modelCell.getValueType());
                    getCells().add(cell);
                }
            }
        }
    }
}
