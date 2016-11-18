package org.apache.camel.component.firebase;

import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.firebase.provider.ConfigurationProvider;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes a dummy message and then checks, if the consumer receives at least one message.
 */
public class FirebaseConsumerTest extends CamelTestSupport {

    @Test
    public void whenFirebaseListener_ShouldReceiveMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String databaseUrl = "gil-sample-app.firebaseio.com";
        final String originalRootReference = "server/saving-data";
        String serviceAccountFile = ConfigurationProvider.createFirebaseConfigLink();
        String rootReference = URLEncoder.encode(originalRootReference, "UTF-8");
        insertDummyData(String.format("https://%s", databaseUrl), originalRootReference, serviceAccountFile);

        return new RouteBuilder() {
            public void configure() {
                try {
                    from(String.format("firebase://" + databaseUrl + "?rootReference=%s&serviceAccountFile=%s",
                            rootReference, serviceAccountFile))
                            .to("log:firebasetest?level=WARN")
                            .to("mock:result");
                } catch (Exception e) {
                    fail(e.toString());
                }
            }
        };
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition wake = lock.newCondition();

    private void insertDummyData(String databaseUrl, String originalRootReference, String serviceAccountFile) throws IOException, InterruptedException {
        FirebaseConfig config = new FirebaseConfig.Builder(databaseUrl, originalRootReference, URLDecoder.decode(serviceAccountFile, "UTF-8"))
                .build();
        config.init();
        FirebaseDatabase
                .getInstance(config.getFirebaseApp())
                .getReference(config.getRootReference()).child("dummy").setValue("test", (databaseError, databaseReference) -> {
                    try {
                        lock.lock();
                        wake.signal();
                    }
                    finally {
                        lock.unlock();
                    }
                });
        try {
            lock.lock();
            wake.await();
        }
        finally {
            lock.unlock();
        }
    }
}