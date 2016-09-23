package com.qbyte.util;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercise the ForgettingMap class.
 * 
 * @author John
 */
public class ForgettingMapTest {

    @Test
    public void whenExcessElementsAddedThenInitialCapacityNotExceeded() {
        for (int test = 1; test <= 10; test++) {
            final int capacity = test * test;
            final ForgettingMap<Integer, Object> map
                    = new HashForgettingMap<>(capacity);
            final int addAttempts = capacity + test;
            for (int attempt = 1; attempt <= addAttempts; attempt++) {
                map.add(attempt, null);
                assertThat(map.size()).isLessThanOrEqualTo(capacity);
            }
        }
    }

    @Test
    public void whenExcessElementsAddedThenLeastFoundElementsDropped() {
        for (int test = 1; test <= 10; test++) {
            final int capacity = test * test;
            final ForgettingMap<Integer, Integer> map
                    = new HashForgettingMap<>(capacity);
            final int addAttempts = capacity + test;
            for (int attempt = 1; attempt <= addAttempts; attempt++) {
                // find all prior elements but the first
                for (int find = attempt - 1; find > 1; find--) {
                    map.find(find);
                }
                Integer forgotten = map.add(attempt, attempt);
                if (attempt == capacity + 1) {
                    assertThat(forgotten).isEqualTo(1);
                } else if (attempt > capacity + 1) {
                    assertThat(forgotten).isEqualTo(attempt - 1);
                }
            }
        }
    }
    
    @Test
    public void whenExcessElementsAddedInBulkModeThenLeastFoundElementsDropped() {
        final int mapSize = 50;
        final ForgettingMap<Integer, Integer> map
                    = new HashForgettingMap<>(mapSize);
        final Map<Integer, Integer> bulk = new HashMap<>();
        for (int bulkno = 0; bulkno < mapSize; bulkno++) {
            bulk.put(bulkno, bulkno);
        }
        map.putAll(bulk);
        assertThat(map.size()).isEqualTo(mapSize);
        map.keySet().parallelStream().forEach((k) -> map.find(k));
        
    }
}
