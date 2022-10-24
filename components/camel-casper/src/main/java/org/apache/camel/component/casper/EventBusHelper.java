package org.apache.camel.component.casper;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Used for demonstrations purpose to simulate some external system event bus/broker, where messages are sent to, and
 * this component can consume from.
 */
@SuppressWarnings("rawtypes")
public class EventBusHelper {
   
    private static EventBusHelper INSTANCE;
  
	private final Set<Consumer> subscribers = ConcurrentHashMap.newKeySet();
    private EventBusHelper() {
    }
    public static EventBusHelper getInstance(){
        if (INSTANCE == null) {
            INSTANCE = new EventBusHelper();
        }
        return INSTANCE;
    }
    public <T> void subscribe(final Consumer<T> subscriber) {
        subscribers.add(subscriber);
    }
    @SuppressWarnings("unchecked")
    public <T> void publish(final T event){
        // Notify all subscribers
        subscribers.forEach(consumer -> publishSingleEvent(event, consumer));
    }
    private <T> void publishSingleEvent(final T event, final Consumer<T> subscriber){
        subscriber.accept(event);
    }
}