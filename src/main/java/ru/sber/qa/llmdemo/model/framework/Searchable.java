package ru.sber.qa.llmdemo.model.framework;

import java.util.Collection;

public interface Searchable<E, V> {


    /**
     * Searches an underlying inventory of items consisting of type E
     *
     * @param value A searchable value of type V
     * @return A Collection of items of type E.
     */

    public Collection<E> search(V value);
}
