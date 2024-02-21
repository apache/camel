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

package org.apache.camel.component.influxdb2.data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InfluxDb write records
 * <p>
 * {@link Record}
 */
public class Records {
    private List<Record> records = new ArrayList<>();

    public Records() {
    }

    private Records(List<Record> records) {
        this.records = records;
    }

    /**
     * get influxdb2 write records
     *
     * @return List<String>
     */
    public List<String> getInfluxRecords() {
        return this.records.stream().map(Record::getInfluxRecord).collect(Collectors.toList());
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }

    public static Records create(List<Record> records) {
        return new Records(records);
    }

    public static Records create(Record recordObj) {
        return new Records().addRecord(recordObj);
    }

    public static Records create(String recordObj) {
        return create(Record.fromString(recordObj));
    }

    public Records addRecord(String recordObj) {
        this.records.add(Record.fromString(recordObj));
        return this;
    }

    public Records addRecord(Record recordObj) {
        this.records.add(recordObj);
        return this;
    }

    @Override
    public String toString() {
        return "Records{" + "records=" + records + '}';
    }
}
