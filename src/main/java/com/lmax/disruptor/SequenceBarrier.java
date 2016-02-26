package com.lmax.disruptor;


/**
 * 给消费者使用的接口,主要用途是用于判断某个位置的Event是否已经可用(可以被消费),如果不可用,等待...
 */
public interface SequenceBarrier {

    /**
     * 等待sequence位置的Event变得可以消费.
     *
     * @param sequence 等待的位置.
     */
    long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;

    /**
     * 获取当前的游标(位置)
     */
    long getCursor();

    /**
     * 表示当前的barrier是否已经被通知过了.
     *
     * @return true表示被通知过, false表示没有被通知.
     */
    boolean isAlerted();

    /**
     * 通知当前的barrier(Event可以被消费了)
     */
    void alert();

    /**
     * 清除当前barrier的通知状态.
     */
    void clearAlert();

    /**
     * 检查当前barrier的通知状态,如果已经被通知,则抛出异常.
     */
    void checkAlert() throws AlertException;
}
