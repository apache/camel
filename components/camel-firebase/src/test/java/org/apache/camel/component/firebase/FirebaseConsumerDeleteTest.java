package org.apache.camel.component.firebase;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.firebase.data.FirebaseMessage;
import org.apache.camel.component.firebase.data.Operation;
import org.apache.camel.component.firebase.provider.ConfigurationProvider;
import org.apache.camel.component.firebase.provider.SampleInputProvider;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts a route which listens to the remove event in Firebase. It then writes and deletes an entry in Firebase and
 * asserts, if the entry was deleted or not.
 */
public class FirebaseConsumerDeleteTest {

    private final ReentrantLock reentrantLock = new ReentrantLock();

    private final Condition wake = reentrantLock.newCondition();

    @Test
    public void whenDelete_DeleteMessageShouldBeIntercepted() throws Exception {
        CamelContext context = new DefaultCamelContext();
        boolean[] deleteMessageReceived = { false };
        FirebaseConfig firebaseConfig = ConfigurationProvider.createDemoConfig();
        setupRoute(context, deleteMessageReceived);
        context.addStartupListener((context1, alreadyStarted) -> {
            TimeUnit.SECONDS.sleep(5);
            createAndDeleteContent(firebaseConfig);
        });
        context.start();
        try {
            reentrantLock.lock();
            wake.await(30, TimeUnit.SECONDS);
        } finally {
            reentrantLock.unlock();
        }
        assertThat(deleteMessageReceived[0]).isTrue();
        context.stop();
    }

    private void createAndDeleteContent(FirebaseConfig firebaseConfig) {
        final DatabaseReference rootReference = FirebaseDatabase.getInstance(firebaseConfig.getFirebaseApp())
                .getReference(ConfigurationProvider.createRootReference()).child(SampleInputProvider.createDeleteKey());
        rootReference
                .setValue("AETHELWULF 839-856", (databaseError, databaseReference) -> {
                    databaseReference.removeValue();
                });
    }

    private void setupRoute(CamelContext context, final boolean[] deleteMessageReceived) throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                try {
                    from(String.format("firebase://%s?rootReference=%s&serviceAccountFile=%s",
                            ConfigurationProvider.createDatabaseUrl(), ConfigurationProvider.createRootReference(), ConfigurationProvider.createFirebaseConfigLink()))
                            .to("log:firebasetest?level=WARN")
                            .process(exchange -> {
                                FirebaseMessage firebaseMessage = (FirebaseMessage) exchange.getIn().getBody();
                                if (firebaseMessage.getOperation() == Operation.CHILD_REMOVED) {
                                    deleteMessageReceived[0] = true;
                                    try {
                                        reentrantLock.lock();
                                        wake.signal();
                                    } finally {
                                        reentrantLock.unlock();
                                    }
                                }
                            });

                } catch (Exception e) {
                    fail(e.toString());
                }
            }
        });
    }
}
