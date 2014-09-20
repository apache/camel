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
package org.apache.camel.component.box.internal;

import java.util.concurrent.CountDownLatch;

import com.box.boxjavalibv2.authorization.IAuthEvent;
import com.box.boxjavalibv2.authorization.IAuthFlowListener;
import com.box.boxjavalibv2.authorization.IAuthFlowMessage;
import com.box.boxjavalibv2.events.OAuthEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* Implementation of {@link IAuthFlowListener} to get success or failure status of OAuth flow.
*/
public final class LoginAuthFlowListener implements IAuthFlowListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAuthFlowListener.class);

    private final Exception[] exception = new Exception[1];
    private final CountDownLatch latch;

    public LoginAuthFlowListener(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onAuthFlowMessage(IAuthFlowMessage message) {
        // do nothing
    }

    @Override
    public void onAuthFlowException(Exception e) {
        // record exception
        exception[0] = e;
        LOG.warn(String.format("OAuth exception: %s", e.getMessage()), e);
        latch.countDown();
    }

    @Override
    public void onAuthFlowEvent(IAuthEvent state, IAuthFlowMessage message) {
        // check success
        if (state == OAuthEvent.OAUTH_CREATED) {
            LOG.debug("OAuth succeeded");
            latch.countDown();
        }
    }

    public Exception getException() {
        return exception[0];
    }
}
