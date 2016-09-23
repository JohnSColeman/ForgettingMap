package com.qbyte.util;

import static java.lang.System.out;
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
}
