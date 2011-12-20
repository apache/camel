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
package org.apache.camel.component.file.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;

public class GenericFileRenameProcessStrategy<T> extends GenericFileProcessStrategySupport<T> {
    private GenericFileRenamer<T> beginRenamer;
    private GenericFileRenamer<T> failureRenamer;
    private GenericFileRenamer<T> commitRenamer;

    public GenericFileRenameProcessStrategy() {
    }

    @Override
    public boolean begin(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {

        // We need to invoke super, but to the file that we are going to use for processing, so we do super after renaming.
        GenericFile<T> to = file;

        if (beginRenamer != null) {
            GenericFile<T> newName = beginRenamer.renameFile(exchange, file);
            to = renameFile(operations, file, newName);
            if (to != null) {
                to.bindToExchange(exchange);
            }
        }
        // must invoke super
        boolean result = super.begin(operations, endpoint, exchange, to);
        if (!result) {
            return false;
        }

        return true;
    }

    @Override
    public void rollback(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        // must invoke super
        super.rollback(operations, endpoint, exchange, file);

        if (failureRenamer != null) {
            // create a copy and bind the file to the exchange to be used by the renamer to evaluate the file name
            Exchange copy = exchange.copy();
            file.bindToExchange(copy);
            // must preserve message id
            copy.getIn().setMessageId(exchange.getIn().getMessageId());
            copy.setExchangeId(exchange.getExchangeId());

            GenericFile<T> newName = failureRenamer.renameFile(copy, file);
            renameFile(operations, file, newName);
        }
    }

    @Override
    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        // must invoke super
        super.commit(operations, endpoint, exchange, file);

        if (commitRenamer != null) {
            // create a copy and bind the file to the exchange to be used by the renamer to evaluate the file name
            Exchange copy = exchange.copy();
            file.bindToExchange(copy);
            // must preserve message id
            copy.getIn().setMessageId(exchange.getIn().getMessageId());
            copy.setExchangeId(exchange.getExchangeId());

            GenericFile<T> newName = commitRenamer.renameFile(copy, file);
            renameFile(operations, file, newName);
        }
    }

    public GenericFileRenamer<T> getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(GenericFileRenamer<T> beginRenamer) {
        this.beginRenamer = beginRenamer;
    }

    public GenericFileRenamer<T> getCommitRenamer() {
        return commitRenamer;
    }

    public void setCommitRenamer(GenericFileRenamer<T> commitRenamer) {
        this.commitRenamer = commitRenamer;
    }

    public GenericFileRenamer<T> getFailureRenamer() {
        return failureRenamer;
    }

    public void setFailureRenamer(GenericFileRenamer<T> failureRenamer) {
        this.failureRenamer = failureRenamer;
    }
}
