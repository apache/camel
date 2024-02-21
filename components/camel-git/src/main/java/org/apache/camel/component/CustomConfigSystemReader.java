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
package org.apache.camel.component;

import java.io.File;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

public class CustomConfigSystemReader extends SystemReader {
    private static final SystemReader PROXY = SystemReader.getInstance();
    private File userGitConfig;

    public CustomConfigSystemReader(File userGitConfig) {
        super();
        this.userGitConfig = userGitConfig;
    }

    @Override
    public String getenv(String variable) {
        return PROXY.getenv(variable);
    }

    @Override
    public String getHostname() {
        return PROXY.getHostname();
    }

    @Override
    public String getProperty(String key) {
        return PROXY.getProperty(key);
    }

    @Override
    public long getCurrentTime() {
        return PROXY.getCurrentTime();
    }

    @Override
    public int getTimezone(long when) {
        return PROXY.getTimezone(when);
    }

    @Override
    public FileBasedConfig openUserConfig(Config parent, FS fs) {
        return new FileBasedConfig(parent, userGitConfig, fs);
    }

    @Override
    public FileBasedConfig openJGitConfig(Config parent, FS fs) {
        return PROXY.openJGitConfig(parent, fs);
    }

    // Return an empty system configuration, based on example in SystemReader.Default#openSystemConfig
    @Override
    public FileBasedConfig openSystemConfig(Config parent, FS fs) {
        return new FileBasedConfig(parent, null, fs) {
            @Override
            public void load() {
            }

            @Override
            public boolean isOutdated() {
                return false;
            }
        };
    }

}
