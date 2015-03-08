package com.googlecode.greysanatomy.clocker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ��ʱ��
 *
 * @author vlinux
 */
public class Timer implements Runnable {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicLong timestamp = new AtomicLong(System.currentTimeMillis());

    private Timer() {
        final Thread timer = new Thread(this, "ga-timer");
        timer.setDaemon(true);
        timer.start();
    }

    @Override
    public void run() {
        while (true) {

            lock.lock();
            // wait 1ms
            try {
                condition.await(1L, TimeUnit.MILLISECONDS);
                // fix timestamp every 1000 times
                if (timestamp.incrementAndGet() % 1000 == 0) {
                    timestamp.set(System.currentTimeMillis());
                }
            } catch (InterruptedException e) {
                // do nothing...
            } finally {
                lock.unlock();
            }

        }
    }

    /**
     * ��ȡ��ʱ����ǰʱ��
     *
     * @return
     */
    public long getCurrentTimeMillis() {
        return timestamp.get();
    }


    private static volatile Timer timer = new Timer();

    /**
     * ��ȡ������clocker
     *
     * @return
     */
    public static Timer current() {
        return timer;
    }

}
