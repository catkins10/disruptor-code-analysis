package com.lmax.disruptor;

import com.lmax.disruptor.util.Util;

import java.util.concurrent.locks.LockSupport;

/**
 * 左边缓存行填充
 */
abstract class SingleProducerSequencerPad extends AbstractSequencer {
    protected long p1, p2, p3, p4, p5, p6, p7;

    public SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }
}

/**
 * 真正需要关心的数据.
 */
abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad {
    public SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    // nextValue表示生产者下一个可以使用的位置的序号,一开始是-1.
    protected long nextValue = Sequence.INITIAL_VALUE;

    // cachedValue表示上一次消费者消费数据时的位置序号,一开始是-1.
    protected long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 * 用于单生产者模式场景, 保存/追踪生产者和消费者的位置序号.
 */
public final class SingleProducerSequencer extends SingleProducerSequencerFields {

    // 右边缓存行填充数据.
    protected long p1, p2, p3, p4, p5, p6, p7;

    /**
     * 使用给定的bufferSize和waitStrategy创建实例.
     */
    public SingleProducerSequencer(int bufferSize, final WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    /**
     * 判断RingBuffer是否还有可用的空间能够容纳requiredCapacity个Event.
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity) {
        long nextValue = this.nextValue;  // 生产者下一个可使用的位置序号

        // 从nextValue位置开始,如果再申请requiredCapacity个位置,将要达到的位置,因为是环形数组,所以减去bufferSize
        // 下面会用该值和消费者的位置序号比较.
        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;

        // 消费者上一次消费的位置, 消费者每次消费之后会更新该值.
        long cachedGatingSequence = this.cachedValue;

        // 先看看这个条件的对立条件: wrapPoint <= cachedGatingSequence && cachedGatingSequence <= nextValue
        // 表示当前生产者走在消费者的前面, 并且就算再申请requiredCapacity个位置达到的位置也不会覆盖消费者上一次消费的位置(就更不用关心
        // 当前消费者消费的位置了,因为消费者消费的位置是一直增大的),这种情况一定能够分配requiredCapacity个空间.

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            // gatingSequences保存的是消费者的当前消费位置, 因为可能有多个消费者, 所以此处获取序号最小的位置.
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            // 顺便更新消费者上一次消费的位置...
            this.cachedValue = minSequence;
            // 如果申请之后的位置会覆盖消费者的位置,则不能分配空间,返回false
            if (wrapPoint > minSequence) {
                return false;
            }
            // 否则返回true.
        }

        return true;
    }

    /**
     * 申请下一个可用空间, 返回该位置的序号, 如果当前没有可用空间, 则一直阻塞直到有可用空间位置.
     */
    @Override
    public long next() {
        return next(1);
    }

    /**
     * 申请n个可用空间, 返回该位置的序号, 如果当前没有可用空间, 则一直阻塞直到有可用空间位置.
     */
    @Override
    public long next(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        long nextValue = this.nextValue;

        long nextSequence = nextValue + n;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        // 这里的判断逻辑和上面的hasAvailableCapacity函数一致, 不多说了.
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            long minSequence;

            // 如果一直没有可用空间, 当前线程挂起, 不断循环检测,直到有可用空间.
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
            // 顺便更新一下消费者消费的位置序号.
            this.cachedValue = minSequence;
        }

        this.nextValue = nextSequence;
        // 返回最后一个可用位置的序号.
        return nextSequence;
    }

    /**
     * 尝试申请一个可用空间, 如果没有,抛出异常.
     */
    @Override
    public long tryNext() throws InsufficientCapacityException {
        return tryNext(1);
    }

    /**
     * 尝试申请n个可用空间,如果没有,抛出异常.
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        // 先调用hasAvailableCapacity函数判断是否能分配, 不能直接抛出异常.
        if (!hasAvailableCapacity(n)) {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * 返回当前RingBuffer的可用位置数目.
     */
    @Override
    public long remainingCapacity() {
        long nextValue = this.nextValue;

        // (多个)消费者消费的最小位置
        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        // 生产者的位置
        long produced = nextValue;
        // 空余的可用的位置数目.
        return getBufferSize() - (produced - consumed);
    }

    /**
     * 更改生产者的位置序号.
     */
    @Override
    public void claim(long sequence) {
        this.nextValue = sequence;
    }

    /**
     * 发布sequence位置的Event.
     */
    @Override
    public void publish(long sequence) {
        // 首先更新生产者游标
        cursor.set(sequence);
        // 然后通知所有消费者, 数据可以被消费了.
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * 发布这个区间内的Event.
     */
    @Override
    public void publish(long lo, long hi) {
        publish(hi);
    }


    /**
     * 判断sequence位置的数据是否已经发布并且可以被消费.
     */
    @Override
    public boolean isAvailable(long sequence) {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
        return availableSequence;
    }
}
