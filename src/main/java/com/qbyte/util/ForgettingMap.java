package com.qbyte.util;

import java.util.Map;

/**
 * Defines a Map that has additional 'forgetting map' behaviours.
 * A 'forgetting map' tracks find operations and keeps values that are most
 * frequently found.
 *
 * @author John Coleman
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface ForgettingMap<K, V> extends Map<K, V> {
    
    /**
     * Add the given key-value pair to the map and return the least frequently 
     * found value value that was removed if the Map was full already or null.
     *
     * @param key a key object
     * @param value a value object associated with the key
     * @return the evicted value or null
     */
    V add(K key, V value);
    
    /**
     * Returns the value object associated with the given key object.
     *
     * @param key a key object
     * @return the value associated with the key if any
     */
    V find(K key);
}
