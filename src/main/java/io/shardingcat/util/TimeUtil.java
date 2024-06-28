
package io.shardingcat.util;

/**
 * 弱精度的计时器，考虑性能不使用同步策略。
 * 
 * @author shardingcat
 */
public class TimeUtil {
    private static volatile long CURRENT_TIME = System.currentTimeMillis();

    public static final long currentTimeMillis() {
        return CURRENT_TIME;
    }
    public static final long currentTimeNanos() {
        return System.nanoTime();
    }

    public static final void update() {
        CURRENT_TIME = System.currentTimeMillis();
    }

}