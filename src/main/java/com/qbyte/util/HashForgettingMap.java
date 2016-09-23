package com.qbyte.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * A thread-safe implementation of a ForgettingMap that wraps a HashMap. This
 * Map cannot be used to return values.
 *
 * @author John Coleman
 *
 * @param <K> key type
 * @param <V> value type
 */
public class HashForgettingMap<K, V> implements ForgettingMap<K, V> {

    /**
     * The lock.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The read lock.
     */
    private final ReadLock readLock = lock.readLock();

    /**
     * The write lock.
     */
    private final WriteLock writeLock = lock.writeLock();

    /**
     * The map.
     */
    private final Map<K, CacheNode<K, V>> map;

    /**
     * The set of frequencies.
     */
    private final LinkedHashSet[] frequencyList;

    /**
     * The lowest frequency.
     */
    private int lowestFrequency;

    /**
     * The maximum frequency.
     */
    private final int maxFrequency;

    /**
     * The maximum capacity of the map.
     */
    private final int maxCapacity;

    /**
     * Instantiate the Map with the given maximum capacity.
     *
     * @param maxCapacity the maximum capacity of this Map.
     */
    public HashForgettingMap(int maxCapacity) {
        this.map = new HashMap<>(maxCapacity);
        this.frequencyList = new LinkedHashSet[maxCapacity];
        this.lowestFrequency = 0;
        this.maxFrequency = maxCapacity - 1;
        this.maxCapacity = maxCapacity;
        initFrequencyList();
    }

    /**
     * Associates the specified value with the specified key in this map. If the
     * map previously contained a mapping for the key, the old value is
     * replaced. If the maximum capacity of the map was exceeded then the
     * evicted value is returned.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the evicted value or <tt>null</tt> if no value was evicted
     */
    @Override
    public V add(K key, V value) {
        writeLock.lock();
        try {
            return this.put(key, value, true);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Associates the specified value with the specified key in this map. If the
     * map previously contained a mapping for the key, the old value is
     * replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>. (A <tt>null</tt>
     * return can also indicate that the map previously associated <tt>null</tt>
     * with <tt>key</tt>.)
     */
    @Override
    public V put(K key, V value) {
        writeLock.lock();
        try {
            return this.put(key, value, false);
        } finally {
            writeLock.unlock();
        }
    }

    private V put(K key, V value, boolean returnEvicted) {
        writeLock.lock();
        try {
            V oldValue = null;
            CacheNode<K, V> currentNode = map.get(key);
            if (currentNode == null) {
                if (map.size() == maxCapacity) {
                    oldValue = returnEvicted ? doEviction() : null;
                }
                LinkedHashSet<CacheNode<K, V>> nodes = frequencyList[0];
                currentNode = new CacheNode(key, value, 0);
                nodes.add(currentNode);
                map.put(key, currentNode);
                lowestFrequency = 0;
            } else {
                oldValue = currentNode.value;
                currentNode.value = value;
            }
            return oldValue;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map. These
     * mappings will replace any mappings that this map had for any of the keys
     * currently in the specified map. Note that bulk insertions that exceed the
     * maximum map capacity cause the previously added elements to be evicted.
     *
     * @param map mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        writeLock.lock();
        try {
            map.entrySet().parallelStream().forEach((e)
                    -> put(e.getKey(), e.getValue()));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the value to which the specified key is mapped, or {@code null}
     * if this map contains no mapping for the key.
     *
     * <p>
     * More formally, if this map contains a mapping from a key {@code k} to a
     * value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise it returns
     * {@code null}. (There can be at most one such mapping.)
     *
     * <p>
     * A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also possible
     * that the map explicitly maps the key to {@code null}. The
     * {@link #containsKey containsKey} operation may be used to distinguish
     * these two cases.
     *
     * @see #put(Object, Object)
     */
    @Override
    public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key).value;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the value to which the specified key is mapped, or {@code null}
     * if this map contains no mapping for the key and increases the frequency
     * for the mapping causing it to survive eviction longer.
     *
     * <p>
     * More formally, if this map contains a mapping from a key {@code k} to a
     * value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise it returns
     * {@code null}. (There can be at most one such mapping.)
     *
     * <p>
     * A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also possible
     * that the map explicitly maps the key to {@code null}. The
     * {@link #containsKey containsKey} operation may be used to distinguish
     * these two cases.
     *
     * @see #put(Object, Object)
     */
    @Override
    public V find(Object key) {
        readLock.lock();
        try {
            CacheNode<K, V> currentNode = map.get(key);
            if (currentNode != null) {
                int currentFrequency = currentNode.frequency;
                if (currentFrequency < maxFrequency) {
                    int nextFrequency = currentFrequency + 1;
                    LinkedHashSet<CacheNode<K, V>> currentNodes = frequencyList[currentFrequency];
                    LinkedHashSet<CacheNode<K, V>> newNodes = frequencyList[nextFrequency];
                    moveToNextFrequency(currentNode, nextFrequency, currentNodes, newNodes);
                    map.put((K) key, currentNode);
                    if (lowestFrequency == currentFrequency && currentNodes.isEmpty()) {
                        lowestFrequency = nextFrequency;
                    }
                } else {
                    LinkedHashSet<CacheNode<K, V>> nodes = frequencyList[currentFrequency];
                    nodes.remove(currentNode);
                    nodes.add(currentNode);
                }
                return currentNode.value;
            } else {
                return null;
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>. (A <tt>null</tt>
     * return can also indicate that the map previously associated <tt>null</tt>
     * with <tt>key</tt>.)
     */
    @Override
    public V remove(Object key) {
        writeLock.lock();
        try {
            CacheNode<K, V> currentNode = map.remove(key);
            if (currentNode != null) {
                LinkedHashSet<CacheNode<K, V>> nodes = frequencyList[currentNode.frequency];
                nodes.remove(currentNode);
                if (lowestFrequency == currentNode.frequency) {
                    findNextLowestFrequency();
                }
                return currentNode.value;
            } else {
                return null;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public int frequencyOf(K key) {
        readLock.lock();
        try {
            CacheNode<K, V> node = map.get(key);
            if (node != null) {
                return node.frequency + 1;
            } else {
                return 0;
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Removes all of the mappings from this map. The map will be empty after
     * this call returns.
     */
    @Override
    public void clear() {
        writeLock.lock();
        try {
            for (int i = 0; i <= maxFrequency; i++) {
                frequencyList[i].clear();
            }
            map.clear();
            lowestFrequency = 0;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    @Override
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    @Override
    public boolean isEmpty() {
        readLock.lock();
        try {
            return this.map.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.
     *
     * @param key The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    @Override
    public boolean containsKey(Object o) {
        readLock.lock();
        try {
            return this.map.containsKey(o);
        } finally {
            readLock.unlock();
        }
    }

    private void initFrequencyList() {
        for (int i = 0; i <= maxFrequency; i++) {
            frequencyList[i] = new LinkedHashSet<>();
        }
    }

    private V doEviction() {
        LinkedHashSet<CacheNode<K, V>> nodes = frequencyList[lowestFrequency];
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Lowest frequency constraint violated!");
        } else {
            Iterator<CacheNode<K, V>> it = nodes.iterator();
            if (it.hasNext()) {
                CacheNode<K, V> node = it.next();
                it.remove();
                return map.remove(node.key).value;
            }
        }
        return null;
    }

    private void moveToNextFrequency(CacheNode<K, V> currentNode,
            int nextFrequency, LinkedHashSet<CacheNode<K, V>> currentNodes, LinkedHashSet<CacheNode<K, V>> newNodes) {
        currentNodes.remove(currentNode);
        newNodes.add(currentNode);
        currentNode.frequency = nextFrequency;
    }

    private void findNextLowestFrequency() {
        while (lowestFrequency <= maxFrequency && frequencyList[lowestFrequency].isEmpty()) {
            lowestFrequency++;
        }
        if (lowestFrequency > maxFrequency) {
            lowestFrequency = 0;
        }
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map. The set is
     * backed by the map, so changes to the map are reflected in the set. If the
     * map is modified while an iteration over the set is in progress the
     * results of the iteration are undefined. The set is unmodifiable.
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
     * Always returns false.
     *
     * @param value value whose presence in this map is to be tested
     * @return false
     */
    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    /**
     * Throws an exception.
     *
     * @return never
     */
    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Throws an exception.
     *
     * @return never
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * A data type class that encapsulates the map value and the associated
     * key and find frequency.
     *
     * @param <K> key  type
     * @param <V> value type
     */
    private static class CacheNode<K, V> {

        public final K key;
        public V value;
        public int frequency;

        public CacheNode(K key, V value, int frequency) {
            this.key = key;
            this.value = value;
            this.frequency = frequency;
        }
    }
}
