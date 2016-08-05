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
package org.apache.camel.dataformat.bindy.model.car;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

/**
 *
 */
@CsvRecord(separator = ";", skipFirstLine = true, quoting = true, crlf = "UNIX")
public class Car {

    @DataField(pos = 1)
    private String stockid;
    @DataField(pos = 2)
    private String make;
    @DataField(pos = 3)
    private String model;
    @DataField(pos = 4)
    private String deriv;
    @DataField(pos = 5)
    private String series;
    @DataField(pos = 6)
    private String registration;
    @DataField(pos = 7)
    private String chassis;
    @DataField(pos = 8)
    private String engine;
    @DataField(pos = 9)
    private int year;
    @DataField(pos = 10, precision = 1)
    private double klms;
    @DataField(pos = 11)
    private String body;
    @DataField(pos = 12)
    private Colour colour;
    @DataField(pos = 13)
    private String enginesize;
    @DataField(pos = 14)
    private String trans;
    @DataField(pos = 15)
    private String fuel;
    @DataField(pos = 16)
    private String options;
    @DataField(pos = 17)
    private String desc;
    @DataField(pos = 18)
    private String status;
    @DataField(pos = 19, precision = 1)
    private double price;
    @DataField(pos = 20)
    private String nvic;

    public String getStockid() {
        return stockid;
    }

    public void setStockid(String stockid) {
        this.stockid = stockid;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDeriv() {
        return deriv;
    }

    public void setDeriv(String deriv) {
        this.deriv = deriv;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public String getRegistration() {
        return registration;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public String getChassis() {
        return chassis;
    }

    public void setChassis(String chassis) {
        this.chassis = chassis;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getKlms() {
        return klms;
    }

    public void setKlms(double klms) {
        this.klms = klms;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Colour getColour() {
        return colour;
    }

    public void setColour(Colour colour) {
        this.colour = colour;
    }

    public String getEnginesize() {
        return enginesize;
    }

    public void setEnginesize(String enginesize) {
        this.enginesize = enginesize;
    }

    public String getTrans() {
        return trans;
    }

    public void setTrans(String trans) {
        this.trans = trans;
    }

    public String getFuel() {
        return fuel;
    }

    public void setFuel(String fuel) {
        this.fuel = fuel;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getNvic() {
        return nvic;
    }

    public void setNvic(String nvic) {
        this.nvic = nvic;
    }

    public enum Colour {
        BLACK
    }

}
