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
package org.apache.camel.component.dropbox.dto;

import com.dropbox.core.DbxEntry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.camel.component.dropbox.util.DropboxConstants.ENTRIES_SIZE;
import static org.apache.camel.component.dropbox.util.DropboxConstants.ENTRIES;

public class DropboxSearchCamelResult extends DropboxCamelResult {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxSearchCamelResult.class);

    @Override
    public void populateExchange(Exchange exchange) {
        if(this.dropboxObjs[0]!=null) {
            List<DbxEntry> entries = (List<DbxEntry>)this.dropboxObjs[0];
            if(entries.size()>0) {
                LOG.info("Entries found: " + entries.size());
                //set info in exchange
                exchange.getIn().setHeader(ENTRIES_SIZE,entries.size());
                exchange.getIn().setBody(entries.size());
                Map<String,String> paths = new HashMap<String,String>(entries.size());
                for(DbxEntry entry:entries) {
                    paths.put(entry.path,entry.name);
                    LOG.info("Entry: " + entry.path+"-"+entry.name);
                }
                exchange.getIn().setHeader(ENTRIES,paths);

            }
        }
    }
}
