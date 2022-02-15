package org.apache.camel.support;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouteWatcherReloadStrategy}
 */
public class RouteWatcherReloadStrategyTest {

    /**
     * This used to fail on Windows because we hardcoded '/' as a file part separator
     * @throws Exception
     */
    @Test
    public void testBasePath() throws Exception{
        RouteWatcherReloadStrategy strategy=new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern("*");
        strategy.setCamelContext(new AbstractExchangeTest.MyCamelContext());
        strategy.doStart();
        assertNotNull(strategy.getFileFilter());
        File folder=new File("./src/test/resources");
        assertTrue(folder.isDirectory());
        File[] fs=folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertEquals(1,fs.length);
        assertEquals("log4j2.properties",fs[0].getName());
    }

    /**
     * Forgetting to set the pattern caused it to be set to null, which caused a NullPointerException in the filter
     * @throws Exception
     */
    @Test
    public void testNullPattern() throws Exception {
        RouteWatcherReloadStrategy strategy=new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern(null);
        strategy.setCamelContext(new AbstractExchangeTest.MyCamelContext());
        strategy.doStart();
        assertNotNull(strategy.getFileFilter());
        File folder=new File("./src/test/resources");
        assertTrue(folder.isDirectory());
        File[] fs=folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertEquals(0,fs.length);
        // null goes back to default
        assertEquals("*.yaml,*.xml",strategy.getPattern());
    }
}
