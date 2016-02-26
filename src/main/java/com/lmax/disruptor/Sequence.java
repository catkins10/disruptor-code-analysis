/*
 * Copyright 2012 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;


/**
 * 左边缓存行填充.
 */
class LhsPadding {
    protected long p1, p2, p3, p4, p5, p6, p7;
}

/**
 * value, 需要保存的真正的值(表示生产者/消费者在RingBuffer上的位置).
 */
class Value extends LhsPadding {
    protected volatile long value;
}

/**
 * 右边缓存行填充.
 */
class RhsPadding extends Value {
    protected long p9, p10, p11, p12, p13, p14, p15;
}

/**
 * 主要用于记录/追踪生产者和消费者在RingBuffer上的位置.
 */
public class Sequence extends RhsPadding {
    static final long INITIAL_VALUE = -1L;
    private static final Unsafe UNSAFE;      // 主要用于实现原子操作.
    private static final long VALUE_OFFSET;  // value的内存位置的偏移量, 后面会根据这个偏移量set/get value

    static {
        UNSAFE = Util.getUnsafe();
        try {
            // 使用UnSafe直接从内存中获取value域的内存位置偏移量.
            VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建一个Sequence, 初始值为-1.
     */
    public Sequence() {
        this(INITIAL_VALUE);
    }

    /**
     * 用指定的初始值创建一个Sequence.
     */
    public Sequence(final long initialValue) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
    }

    /**
     * 获取value的值.
     */
    public long get() {
        return value;
    }

    /**
     * 使用UnSafe在指定的内存位置设置value的值.
     */
    public void set(final long value) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
    }

    /**
     * 使用UnSafe在指定的内存位置设置value的值.
     */
    public void setVolatile(final long value) {
        UNSAFE.putLongVolatile(this, VALUE_OFFSET, value);
    }

    /**
     * CAS操作,先比较value的原值是不是expectedValue,如果是更新为newValue,否则不做任何操作.
     */
    public boolean compareAndSet(final long expectedValue, final long newValue) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
    }

    /**
     * 将value的值加一,使用了CAS做原子操作.
     */
    public long incrementAndGet() {
        return addAndGet(1L);
    }

    /**
     * 将value增加一个指定的值,使用CAS做原子操作.
     */
    public long addAndGet(final long increment) {
        long currentValue;
        long newValue;

        do {
            currentValue = get();
            newValue = currentValue + increment;
        }
        while (!compareAndSet(currentValue, newValue));

        return newValue;
    }

    @Override
    public String toString() {
        return Long.toString(get());
    }
}
