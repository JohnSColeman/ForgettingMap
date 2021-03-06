package com.qbyte.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercise the ForgettingMap class.
 *
 * @author John
 */
public class ForgettingMapTest {

    @Test
    public void whenELementAdded_ThenElementIsMapped() {
        final ForgettingMap<Integer, Integer> map
                = new HashForgettingMap<>(1);
        map.put(1, 1);
        assertThat(map.get(1)).isEqualTo(1);
    }

    @Test
    public void whenELementAdded_ThenElementRemoved_ThenMapIsEmpty() {
        final ForgettingMap<Integer, Integer> map
                = new HashForgettingMap<>(1);
        map.put(1, 1);
        map.remove(1);
        assertThat(map).isEmpty();
    }

    @Test
    public void whenExcessElementsAdded_ThenInitialCapacityNotExceeded() {
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
    public void whenExcessElementsAdded_ThenLeastFoundElementsDropped() {
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
    public void whenExcessElementsAddedInBulkMode_ThenLeastFoundElementsDropped() {
        final int mapSize = 50;
        final ForgettingMap<Integer, Integer> map
                = new HashForgettingMap<>(mapSize);
        final Map<Integer, Integer> bulk = new HashMap<>();
        for (int bulkno = 1; bulkno <= mapSize; bulkno++) {
            bulk.put(bulkno, bulkno);
        }
        map.putAll(bulk);
        assertThat(map).hasSize(mapSize);
        Set<Integer> unfoundKeys = new HashSet();
        unfoundKeys.add(new Random().nextInt(mapSize) + 1);
        unfoundKeys.add(new Random().nextInt(mapSize) + 1);
        unfoundKeys.add(new Random().nextInt(mapSize) + 1);
        map.keySet().forEach((k) -> {
            if (!unfoundKeys.contains(k)) {
                map.find(k);
            }
        });
        final Map<Integer, Integer> bulk2 = new HashMap<>();
        for (int bulkno = mapSize + 1; bulkno <= mapSize * 2; bulkno++) {
            bulk2.put(bulkno, bulkno);
        }
        map.putAll(bulk2);
        assertThat(map).hasSize(mapSize);
        assertThat(map).containsKey(100); // last key inserted always added
        Integer[] unfoundKs = new Integer[unfoundKeys.size()];
        unfoundKs = unfoundKeys.toArray(unfoundKs);
        assertThat(map).doesNotContainKeys(unfoundKs);
    }

}
