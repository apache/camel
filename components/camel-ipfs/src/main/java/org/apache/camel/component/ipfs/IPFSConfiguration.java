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
package org.apache.camel.component.ipfs;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class IPFSConfiguration {

    // Available commands
    public enum IPFSCommand {
        add, cat, get, version
    }

    @UriPath(description = "The ipfs command", enums = "add,cat,get,version") @Metadata(required = true)
    private String ipfsCmd;
    @UriParam(description = "The ipfs output directory")
    private String outdir;

    public void init(String urispec, String remaining) throws Exception {
        String cmd = remaining;
        setIpfsCmd(cmd);
    }

    public String getIpfsCmd() {
        return ipfsCmd;
    }

    public void setIpfsCmd(String cmd) {
        this.ipfsCmd = cmd;
    }

    public String getOutdir() {
        return outdir;
    }

    public void setOutdir(String outdir) {
        this.outdir = outdir;
    }
}
