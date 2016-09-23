package com.qbyte.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe implementation of a ForgettingMap that wraps a HashMap.
 *
 * @author John Coleman
 * @param <K>
 * @param <V>
 */
public class HashForgettingMap<K, V> implements ForgettingMap<K, V> {

    /**
     * CompoundKey provides inconsistent equals and compareTo behaviours, equals
     * is based on the key, compareTo is based on the hitCount. This implements
     * the difference between equality and ordering as required.
     *
     * @param <K> the key
     */
    static class CompoundKey<K> implements Comparable<CompoundKey<K>> {

        int hitCount;

        K key;

        public CompoundKey(K key) {
            this.key = key;
        }

        @Override
        public int compareTo(CompoundKey<K> other) {
            if (equals(other)) {
                return 0;
            } else {
                System.out.println("HC COMP");
                int comp = this.hitCount - other.hitCount;
                return comp != 0? comp : 1;
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + this.hitCount;
            hash = 97 * hash + Objects.hashCode(this.key);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CompoundKey<?> other = (CompoundKey<?>) obj;
            System.out.println("EQ " + this.key.equals(other.key));
            return this.key.equals(other.key);
        }
        
        @Override
        public String toString() {
            return "CompoundKey{" + "hitCount=" + hitCount + ", key=" + key + '}';
        }
    }

    /**
     * The maximum capacity for this Map.
     */
    private final int capacity;

    /**
     * An ordered Map of the compoundKeys.
     */
    private final TreeMap<CompoundKey<K>, CompoundKey<K>> keys = new TreeMap();

    /**
     * The Map used to implement this Map.
     */
    private final Map<K, V> map = new HashMap<>();

    /**
     * The lock for controlling read/write access.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The read lock.
     */
    private final Lock readLock = lock.readLock();

    /**
     * The write lock.
     */
    private final Lock writeLock = lock.writeLock();

    /**
     * Constructs a ForgettingMap with the given maximum capacity.
     *
     * @param capacity the maximum capacity for this ForgettingMap
     */
    public HashForgettingMap(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Returns the maximum capacity of this Map.
     *
     * @return the maximum capacity for this ForgettingMap
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Adds a key-value pair to this ForgettingMap and removes the mapping with
     * the least hits made by the find method if the size exceeds the maximum
     * capacity.
     *
     * @param key
     * @param value
     */
    @Override
    public synchronized void add(K key, V value) {
        writeLock.lock();
        try {
            this.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the value associated with the given key and updates the keys hit
     * count or returns null if the key is not found.
     *
     * @param key the key of the value to return
     * @return the element associated with the key or null
     */
    @Override
    public V find(K key) {
        readLock.lock();
        try {
            CompoundKey compKey = keys.get(new CompoundKey(key));
            if (compKey != null) {
                ++compKey.hitCount;
                keys.put(compKey, compKey);
                return map.get(key);
            } else {
                return null;
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        readLock.lock();
        try {
            return map.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        readLock.lock();
        try {
            return map.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean containsValue(Object value) {
        readLock.lock();
        try {
            return map.containsValue(value);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Adds a key-value pair to this ForgettingMap and removes the mapping with
     * the least hits made by the find method if the new size would exceed the
     * maximum capacity.
     *
     * @param key the key of the associated value
     * @param value the value to add to this Map
     */
    @Override
    public V put(K key, V value) {
        writeLock.lock();
        try {
            CompoundKey compKey = new CompoundKey(key);
            System.out.println("KEY " + key + " CONTAINS " + keys.containsKey(compKey));
            if (!keys.containsKey(compKey)) {
                if (keys.size() + 1 > capacity) {
                    CompoundKey remKey = keys.firstKey();
                    System.out.println("KEY TO REMOVE " + compKey);
                    map.remove(remKey.key);
                    Object removed = keys.remove(remKey);
                    System.out.println("SIZES Ks=" + keys.size() + " Ms" + map.size() + " REM " + removed);
                }
                keys.put(compKey, compKey);
            }
            return map.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public synchronized V remove(Object key) {
        writeLock.lock();
        try {
            keys.remove(new CompoundKey(key));
            return map.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        writeLock.lock();
        try {
            m.entrySet().parallelStream().forEach((e)
                    -> put(e.getKey(), e.getValue()));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            map.clear();
            keys.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns an unmodifiable version of this Maps key set.
     *
     * @return this Maps key set
     */
    @Override
    public Set<K> keySet() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(map.keySet());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns an unmodifiable version of the this Maps values.
     *
     * @return this Maps values
     */
    @Override
    public Collection<V> values() {
        readLock.lock();
        try {
            return Collections.unmodifiableCollection(map.values());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns an unmodifiable version of this Maps entry set.
     *
     * @return this maps entry set
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(map.entrySet());
        } finally {
            readLock.unlock();
        }
    }
}
