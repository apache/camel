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

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class WekaConfiguration {

    // Available commands
    public enum Command {
        filter, read, write, version 
    }

    @UriPath(description = "The filter command")
    private String filter;
    @UriPath(description = "The version command")
    private String version;
    @UriParam(description = "The Weka filter/classifier spec (i.e. Name [Options])")
    private String apply;
    @UriParam(description = "An optional in/out path for the read/write commands")
    private String path;

    private Command command;
    
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

    String getPath() {
        return path;
    }

    void setPath(String path) {
        this.path = path;
    }
}
