This project provides an implementation of a "forgetting map". A forgetting map
has a maximum capacity. When new mappings are added to a map at full capacity
a least frequently used mapping by the find method is evicted and the new mapping
is then added.

The Maven Cobertura reports may not present properly due to Java 8.

Tests do not cover thread safety.
