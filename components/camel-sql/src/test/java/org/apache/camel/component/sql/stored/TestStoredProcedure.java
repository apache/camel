package org.apache.camel.component.sql.stored;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by snurmine on 12/20/15.
 */
public class TestStoredProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(TestStoredProcedure.class);


    public static void addnumbers(int val1, int val2, int[] ret) {
        LOG.info("calling addnumbers:{} + {}", val1, val2);

        ret[0] = val1 + val2;

    }

    public static void niladic() {
        LOG.info("nilacid called");
    }
}
