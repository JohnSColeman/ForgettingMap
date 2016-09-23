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
 * A thread-safe implementation of a ForgettingMap that wraps a HashMap.
 *
 * @author John Coleman
 *
 * @param <K>
 * @param <V>
 */
public class HashForgettingMap<K, V> implements ForgettingMap<K, V> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ReadLock readLock = lock.readLock();

    private final WriteLock writeLock = lock.writeLock();

    private final Map<K, CacheNode<K, V>> map;

    private final LinkedHashSet[] frequencyList;

    private int lowestFrequency;

    private final int maxFrequency;
    //
    private final int maxCapacity;

    /**
     *
     * @param maxCapacity
     */
    public HashForgettingMap(int maxCapacity) {
        this.map = new HashMap<>(maxCapacity);
        this.frequencyList = new LinkedHashSet[maxCapacity];
        this.lowestFrequency = 0;
        this.maxFrequency = maxCapacity - 1;
        this.maxCapacity = maxCapacity;
        initFrequencyList();
    }

    @Override
    public V add(K key, V value) {
        writeLock.lock();
        try {
            return this.put(key, value, true);
        } finally {
            writeLock.unlock();
        }
    }

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

    @Override
    public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key).value;
        } finally {
            readLock.unlock();
        }
    }

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
                    // Hybrid with LRU: put most recently accessed ahead of others:
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
            return this.map.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

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

    @Override
    public Set<K> keySet() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(map.keySet());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean containsValue(Object o) {
        return false;
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     *
     * @param <K>
     * @param <V>
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
