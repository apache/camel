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
package org.apache.camel.dataformat.bindy.model.csv;

import java.io.Serializable;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", skipFirstLine = false, endWithLineBreak = false)
public class MyCsvRecord2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @DataField(pos = 1)
    private String attention;
    @DataField(pos = 2)
    private String addressLine1;
    @DataField(pos = 3)
    private String addressLine2;
    @DataField(pos = 4)
    private String city;
    @DataField(pos = 5)
    private String state;
    @DataField(pos = 6)
    private String zip;
    @DataField(pos = 7)
    private String country;
    @DataField(pos = 8)
    private String dummy1;
    @DataField(pos = 9)
    private String dummy2;

    public MyCsvRecord2() {
    }

    public String getAttention() {
        return attention;
    }

    public void setAttention(String attention) {
        this.attention = attention;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDummy1() {
        return dummy1;
    }

    public void setDummy1(String dummy1) {
        this.dummy1 = dummy1;
    }

    public String getDummy2() {
        return dummy2;
    }

    public void setDummy2(String dummy2) {
        this.dummy2 = dummy2;
    }

    @Override
    public String toString() {
        return "Record [attention=" + attention + ", addressLine1=" + addressLine1 + ", addressLine2="
               + addressLine2 + ", city=" + city + ", state=" + state + ", zip=" + zip + ", country="
               + country + ", dummy1=" + dummy1 + ", dummy2=" + dummy2 + "]";
    }

}
