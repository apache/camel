package org.apache.camel.impl;

import org.apache.camel.spi.ThreadPoolProfile;

/**
 * Use ThreadPoolProfile instead
 */
@Deprecated
public class ThreadPoolProfileSupport extends ThreadPoolProfile {

    public ThreadPoolProfileSupport(String id) {
        super(id);
    }

}
