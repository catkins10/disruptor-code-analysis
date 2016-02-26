package com.lmax.disruptor;

public interface Sequenced {

    /**
     * 返回当前RingBuffer的大小.
     */
    int getBufferSize();

    /**
     * 判断当前的RingBuffer是否还有足够的空间可以容纳requiredCapacity个Event.
     */
    boolean hasAvailableCapacity(final int requiredCapacity);

    /**
     * 返回当前RingBuffer可用的空间数目.
     */
    long remainingCapacity();

    /**
     * 返回当前RingBuffer上可以给生产者发布Event的位置的序号.
     */
    long next();

    /**
     * 向RingBuffer申请n个可用空间给生产者发布Event.主要用于批量发布的场景,使用该函数需要做一些额外的计算,
     * 比如: 如果需要申请的个数n=10, 则调用该函数之后会返回最后一个可以使用的位置的需要high:
     * long high = next(10);
     * 拿到high之后就可以算出第一个可以用的位置的序号low:
     * long low = high - (n - 1);
     * 最后生产者需要向[low, high]这个区间的位置填充Event:
     * for (int i = low; i <= high; i++) {
     * // 填充该位置的数据.
     * }
     * 最后再通知消费者这些区间内的数据可以被消费:
     * ringBuffer.publish(low, high);
     */
    long next(int n);

    /**
     * 尝试向RingBuffer申请一个可用空间, 如果有,则返回该可用空间的位置序号,否则抛出异常.
     */
    long tryNext() throws InsufficientCapacityException;

    /**
     * 尝试向RingBuffer申请n个可用空间,如果有,则返回这些可用空间中最后一个空间的位置序号,否则抛出异常.
     */
    long tryNext(int n) throws InsufficientCapacityException;

    /**
     * 发布该位置的Event(通知消费者可以消费了), 需要注意的是调用该函数之前需要先将该位置的数据填充上.
     */
    void publish(long sequence);

    /**
     * 发布[lo, hi]区间的Event(通知消费者可以消费了),需要注意的是调用该函数之前需要先将这些位置的数据填充上
     */
    void publish(long lo, long hi);
}