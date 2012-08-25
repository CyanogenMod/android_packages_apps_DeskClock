// ITimerClockService.aidl
package com.android.deskclock;

/**
 * An interface that defines the method implemented by {@link TimerClockService}
 */
interface ITimerClockService {
    /**
     * Method that creates a new timer.
     *
     * @param type The type of timer to create.
     * @return long The identifier of the timer created.
     * @see TimerClockService.TIMER_TYPE
     */
    long createTimer(int type);

    /**
     * Method that removes a timer from the service.
     *
     * @param timerId The identifier of the timer
     */
    void destroyTimer(long timerId);

    /**
     * Method that starts a timer from the service.
     *
     * @param timerId The identifier of the timer
     */
    void startTimer(long timerId);

    /**
     * Method that stops a timer from the service.
     *
     * @param timerId The identifier of the timer
     */
    void stopTimer(long timerId);

    /**
     * Method that returns the time in milliseconds of the timer
     *
     * @param timerId The identifier of the timer
     * @return long The time in milliseconds
     */
    long queryTime(long timerId);

    /**
     * Method that returns the time in milliseconds of the timer
     *
     * @param timerId The identifier of the timer
     * @param time The milliseconds of the countdown timer
     */
    void setCountDownTime(long timerId, long time);

    /**
     * Method that returns if timer is actually computing time
     *
     * @param timerId The identifier of the timer
     * @return boolean If the timer is running or not
     */
    boolean isRunning(long timerId);

    /**
     * Method that removes the notification of the timer (if exists)
     *
     * @param timerId The identifier of the timer
     */
    void removeNotification(long timerId);
}
