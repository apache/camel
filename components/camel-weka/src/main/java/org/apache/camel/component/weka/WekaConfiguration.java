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
package org.apache.camel.component.weka;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class WekaConfiguration {

    // Available commands
    public enum Command {
        filter, model, read, write, push, pop, version 
    }

    @UriPath(description = "The command to use.", enums = "filter,model,read,write,push,pop,version")
    @Metadata(required = true)
    private Command command;

    // Read/Write parameters
    @UriParam(description = "An in/out path for the read/write commands", label = "read,write")
    private String path;

    // Filter parameters
    @UriParam(description = "The filter spec (i.e. Name [Options])", label = "filter")
    private String apply;
    
    // Model parameters
    @UriParam(description = "The classifier spec (i.e. Name [Options])", label = "model")
    private String build;
    @UriParam(description = "Flag on whether to use cross-validation with the current dataset", label = "model")
    private boolean xval;
    @UriParam(description = "The named dataset to train the classifier with", label = "model")
    private String dsname;
    @UriParam(description = "Number of folds to use for cross-validation", label = "model", defaultValue = "10")
    private int folds = 10;
    @UriParam(description = "An optional seed for the randomizer", label = "model", defaultValue = "1")
    private int seed = 1;
    @UriParam(description = "Path to save the model to", label = "model")
    private String saveTo;
    @UriParam(description = "Path to load the model from", label = "model")
    private String loadFrom;
    

    Command getCommand() {
        return command;
    }

    void setCommand(Command command) {
        this.command = command;
    }

    String getApply() {
        return apply;
    }

    void setApply(String apply) {
        this.apply = apply;
    }

    String getBuild() {
        return build;
    }

    void setBuild(String build) {
        this.build = build;
    }

    String getPath() {
        return path;
    }

    void setPath(String path) {
        this.path = path;
    }

    boolean isXval() {
        return xval;
    }

    void setXval(boolean xval) {
        this.xval = xval;
    }

    int getFolds() {
        return folds;
    }

    void setFolds(int folds) {
        this.folds = folds;
    }

    int getSeed() {
        return seed;
    }

    void setSeed(int seed) {
        this.seed = seed;
    }

    String getSaveTo() {
        return saveTo;
    }

    void setSaveTo(String saveTo) {
        this.saveTo = saveTo;
    }

    String getLoadFrom() {
        return loadFrom;
    }

    void setLoadFrom(String loadFrom) {
        this.loadFrom = loadFrom;
    }

    String getDsname() {
        return dsname;
    }

    void setDsname(String dsname) {
        this.dsname = dsname;
    }

}
