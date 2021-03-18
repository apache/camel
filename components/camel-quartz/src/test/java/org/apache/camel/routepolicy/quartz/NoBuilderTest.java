package org.apache.camel.routepolicy.quartz;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NoBuilderTest extends CamelTestSupport {

    static final Logger LOG = LoggerFactory.getLogger(SimpleScheduledRoutePolicyTest.class);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
