package org.apache.camel.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestConsumerContextPathMatcherTest {

    private static class MockConsumerPath implements RestConsumerContextPathMatcher.ConsumerPath {
        private final String method;
        private final String consumerPath;

        private MockConsumerPath(String method, String consumerPath) {
            this.method = method;
            this.consumerPath = consumerPath;
        }

        @Override
        public String getRestrictMethod() {
            return method;
        }

        @Override
        public String getConsumerPath() {
            return consumerPath;
        }

        @Override
        public Object getConsumer() {
            return null;
        }

        @Override
        public boolean isMatchOnUriPrefix() {
            return false;
        }
    }

    @Test
    public void testRestConsumerContextPathMatcherWithAmbiguousPaths() {
        List<RestConsumerContextPathMatcher.ConsumerPath> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET","/camel/{a}/b/{c}"));
        consumerPaths.add(new MockConsumerPath("GET","/camel/a/{b}/{c}"));

        IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> {
            RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/a/b/3", consumerPaths);
        });
        assertEquals("Ambiguous paths /camel/{a}/b/{c},/camel/a/{b}/{c} for request path /camel/a/b/3",
                illegalStateException.getMessage());
    }


    @Test
    public void testRestConsumerContextPathMatcherSuccess() {
        List<RestConsumerContextPathMatcher.ConsumerPath> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET","/camel/a/b/{c}"));
        consumerPaths.add(new MockConsumerPath("GET","/camel/aa/{b}/{c}"));

        RestConsumerContextPathMatcher.ConsumerPath path =
                RestConsumerContextPathMatcher.matchBestPath("GET",
                        "/camel/a/b/3", consumerPaths);
        assertEquals(path.getConsumerPath(), "/camel/a/b/{c}");
    }
}
