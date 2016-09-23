package com.qbyte.util;

import java.util.Map;

/**
 * Defines a Map that has additional 'forgetting map' behaviours.
 * A 'forgetting map' tracks find operations and keeps values that are most
 * frequently found.
 *
 * @author John Coleman
 * @param <K>
 * @param <V>
 */
public interface ForgettingMap<K, V> extends Map<K, V> {
    
    /**
     * Add the given key-value pair to the map.
     *
     * @param key a key object
     * @param value a value object associated with the key
     */
    void add(K key, V value);
    
    /**
     * Returns the value object associated with the given key object.
     *
     * @param key a key object
     * @return the value associated with the key if any
     */
    V find(K key);
}
