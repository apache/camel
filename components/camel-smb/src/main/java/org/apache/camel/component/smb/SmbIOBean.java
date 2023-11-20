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

package org.apache.camel.component.smb;

import java.util.Set;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;

public interface SmbIOBean {

    /**
     * The access mask to use when opening a file
     *
     * @return
     */
    Set<AccessMask> accessMask();

    /**
     * The attributes to request when opening a file
     *
     * @return
     */
    Set<FileAttributes> attributes();

    /**
     * The share access to request when opening a file
     *
     * @return
     */
    Set<SMB2ShareAccess> shareAccesses();

    /**
     * The create disposition to use when opening a file
     *
     * @return
     */
    SMB2CreateDisposition createDisposition();

    /**
     * The file create options (if applicable)
     *
     * @return
     */
    Set<SMB2CreateOptions> createOptions();

}
