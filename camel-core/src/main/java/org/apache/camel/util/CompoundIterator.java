package org.apache.camel.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CompoundIterator<T> implements Iterator<T> {

    final Iterator<Iterator<T>> it;
    Iterator<T> current;

    public CompoundIterator(Iterable<Iterator<T>> it) {
        this(it.iterator());
    }

    public CompoundIterator(Iterator<Iterator<T>> it) {
        this.it = it;
        this.current = it.hasNext() ? it.next() : null;
    }

    @Override
    public boolean hasNext() {
        while (current != null) {
            if (current.hasNext()) {
                return true;
            } else {
                current = it.hasNext() ? it.next() : null;
            }

        }
        return false;
    }

    @Override
    public T next() {
        if (current != null) {
            return current.next();
        }
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        current.remove();
    }
}
