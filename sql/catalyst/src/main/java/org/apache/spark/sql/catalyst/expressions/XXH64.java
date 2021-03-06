/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.sql.catalyst.expressions;

import org.apache.spark.unsafe.memory.MemoryBlock;

// scalastyle: off
/**
 * xxHash64. A high quality and fast 64 bit hash code by Yann Colet and Mathias Westerdahl. The
 * class below is modelled like its Murmur3_x86_32 cousin.
 * <p/>
 * This was largely based on the following (original) C and Java implementations:
 * https://github.com/Cyan4973/xxHash/blob/master/xxhash.c
 * https://github.com/OpenHFT/Zero-Allocation-Hashing/blob/master/src/main/java/net/openhft/hashing/XxHash_r39.java
 * https://github.com/airlift/slice/blob/master/src/main/java/io/airlift/slice/XxHash64.java
 */
// scalastyle: on
public final class XXH64 {

  private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
  private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
  private static final long PRIME64_3 = 0x165667B19E3779F9L;
  private static final long PRIME64_4 = 0x85EBCA77C2B2AE63L;
  private static final long PRIME64_5 = 0x27D4EB2F165667C5L;

  private final long seed;

  public XXH64(long seed) {
    super();
    this.seed = seed;
  }

  @Override
  public String toString() {
    return "xxHash64(seed=" + seed + ")";
  }

  public long hashInt(int input) {
    return hashInt(input, seed);
  }

  public static long hashInt(int input, long seed) {
    long hash = seed + PRIME64_5 + 4L;
    hash ^= (input & 0xFFFFFFFFL) * PRIME64_1;
    hash = Long.rotateLeft(hash, 23) * PRIME64_2 + PRIME64_3;
    return fmix(hash);
  }

  public long hashLong(long input) {
    return hashLong(input, seed);
  }

  public static long hashLong(long input, long seed) {
    long hash = seed + PRIME64_5 + 8L;
    hash ^= Long.rotateLeft(input * PRIME64_2, 31) * PRIME64_1;
    hash = Long.rotateLeft(hash, 27) * PRIME64_1 + PRIME64_4;
    return fmix(hash);
  }

  public long hashUnsafeWordsBlock(MemoryBlock mb) {
    return hashUnsafeWordsBlock(mb, seed);
  }

  public static long hashUnsafeWordsBlock(MemoryBlock mb, long seed) {
    assert (mb.size() % 8 == 0) : "lengthInBytes must be a multiple of 8 (word-aligned)";
    long hash = hashBytesByWordsBlock(mb, seed);
    return fmix(hash);
  }

  public long hashUnsafeBytes(Object base, long offset, int length) {
    return hashUnsafeBytes(base, offset, length, seed);
  }

  public static long hashUnsafeBytesBlock(MemoryBlock mb, long seed) {
    long offset = 0;
    long length = mb.size();
    assert (length >= 0) : "lengthInBytes cannot be negative";
    long hash = hashBytesByWordsBlock(mb, seed);
    long end = offset + length;
    offset += length & -8;

    if (offset + 4L <= end) {
      hash ^= (mb.getInt(offset) & 0xFFFFFFFFL) * PRIME64_1;
      hash = Long.rotateLeft(hash, 23) * PRIME64_2 + PRIME64_3;
      offset += 4L;
    }

    while (offset < end) {
      hash ^= (mb.getByte(offset) & 0xFFL) * PRIME64_5;
      hash = Long.rotateLeft(hash, 11) * PRIME64_1;
      offset++;
    }
    return fmix(hash);
  }

  public static long hashUnsafeBytes(Object base, long offset, int length, long seed) {
    return hashUnsafeBytesBlock(MemoryBlock.allocateFromObject(base, offset, length), seed);
  }

  private static long fmix(long hash) {
    hash ^= hash >>> 33;
    hash *= PRIME64_2;
    hash ^= hash >>> 29;
    hash *= PRIME64_3;
    hash ^= hash >>> 32;
    return hash;
  }

  private static long hashBytesByWordsBlock(MemoryBlock mb, long seed) {
    long offset = 0;
    long length = mb.size();
    long hash;
    if (length >= 32) {
      long limit = length - 32;
      long v1 = seed + PRIME64_1 + PRIME64_2;
      long v2 = seed + PRIME64_2;
      long v3 = seed;
      long v4 = seed - PRIME64_1;

      do {
        v1 += mb.getLong(offset) * PRIME64_2;
        v1 = Long.rotateLeft(v1, 31);
        v1 *= PRIME64_1;

        v2 += mb.getLong(offset + 8) * PRIME64_2;
        v2 = Long.rotateLeft(v2, 31);
        v2 *= PRIME64_1;

        v3 += mb.getLong(offset + 16) * PRIME64_2;
        v3 = Long.rotateLeft(v3, 31);
        v3 *= PRIME64_1;

        v4 += mb.getLong(offset + 24) * PRIME64_2;
        v4 = Long.rotateLeft(v4, 31);
        v4 *= PRIME64_1;

        offset += 32L;
      } while (offset <= limit);

      hash = Long.rotateLeft(v1, 1)
              + Long.rotateLeft(v2, 7)
              + Long.rotateLeft(v3, 12)
              + Long.rotateLeft(v4, 18);

      v1 *= PRIME64_2;
      v1 = Long.rotateLeft(v1, 31);
      v1 *= PRIME64_1;
      hash ^= v1;
      hash = hash * PRIME64_1 + PRIME64_4;

      v2 *= PRIME64_2;
      v2 = Long.rotateLeft(v2, 31);
      v2 *= PRIME64_1;
      hash ^= v2;
      hash = hash * PRIME64_1 + PRIME64_4;

      v3 *= PRIME64_2;
      v3 = Long.rotateLeft(v3, 31);
      v3 *= PRIME64_1;
      hash ^= v3;
      hash = hash * PRIME64_1 + PRIME64_4;

      v4 *= PRIME64_2;
      v4 = Long.rotateLeft(v4, 31);
      v4 *= PRIME64_1;
      hash ^= v4;
      hash = hash * PRIME64_1 + PRIME64_4;
    } else {
      hash = seed + PRIME64_5;
    }

    hash += length;

    long limit = length - 8;
    while (offset <= limit) {
      long k1 = mb.getLong(offset);
      hash ^= Long.rotateLeft(k1 * PRIME64_2, 31) * PRIME64_1;
      hash = Long.rotateLeft(hash, 27) * PRIME64_1 + PRIME64_4;
      offset += 8L;
    }
    return hash;
  }
}
