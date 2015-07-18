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
package org.apache.camel.component.git;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.git.consumer.GitCommitConsumer;
import org.apache.camel.component.git.consumer.GitTagConsumer;
import org.apache.camel.component.git.consumer.GitType;
import org.apache.camel.component.git.producer.GitProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "git", title = "Git", syntax = "git://localpath", label = "api,file")
public class GitEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private String localPath;
    @UriPath
    private String branchName;
    @UriPath(label = "consumer")
    private GitType type;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam
    private String remotePath;
    @UriParam
    private String operation;

    public GitEndpoint(String uri, GitComponent component) {
        super(uri, component);
    }
    
	@Override
	public Producer createProducer() throws Exception {
		return new GitProducer(this);
	}

	@Override
	public Consumer createConsumer(Processor processor) throws Exception {
	    if (type == GitType.COMMIT) return new GitCommitConsumer(this, processor);
	    else if (type == GitType.TAG) return new GitTagConsumer(this, processor);
	    else throw new IllegalArgumentException("Cannot create producer with type " + type);
	}

	@Override
	public boolean isSingleton() {
		// TODO Auto-generated method stub
		return false;
	} 

	public String getRemotePath() {
		return remotePath;
	}

	public void setRemotePath(String remotePath) {
		this.remotePath = remotePath;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getLocalPath() {
		return localPath;
	}

	public void setLocalPath(String localPath) {
		this.localPath = localPath;
	}

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

    public GitType getType() {
        return type;
    }

    public void setType(GitType type) {
        this.type = type;
    }
}
