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
package org.apache.camel.dataformat.bindy.csv;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", skipFirstLine = true, quoting = true, generateHeaderColumns = true)
public class WickedHeaderWithCommaCsv {

    @DataField(columnName = "Foo (one, or more, foos)", pos = 1)
    private String foo;

    @DataField(columnName = "Bar (one, or more, bars)", pos = 2)
    private String bar;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WickedHeaderWithCommaCsv wickedHeaderWithCommaCsv = (WickedHeaderWithCommaCsv) o;

        if (foo != null ? !foo.equals(wickedHeaderWithCommaCsv.foo) : wickedHeaderWithCommaCsv.foo != null) {
            return false;
        }
        return bar != null ? bar.equals(wickedHeaderWithCommaCsv.bar) : wickedHeaderWithCommaCsv.bar == null;
    }

    @Override
    public int hashCode() {
        int result = foo != null ? foo.hashCode() : 0;
        result = 31 * result + (bar != null ? bar.hashCode() : 0);
        return result;
    }

}
