package com.microsoft.applicationinsights.internal.channel.samplingV2;

import java.util.concurrent.atomic.AtomicLong;

class ExponentialMovingAverageCounter {

    //Average value of counter
    private double average;

    //Value of counter doing current interval of time
    private AtomicLong current;

    //Gets exponential coefficient(must be between 0 - 1)
    public double coefficient;

    // Initializes a new instance of ExponentialMovingAverageCounter
    public ExponentialMovingAverageCounter(double coefficient) {
        this.coefficient = coefficient;
    }

    //Increments value
    public long increment() {
        return current.incrementAndGet();
    }

    //Gets the current value of coefficient
    public double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    //gets the exponential moving average value of the counter
    public double getAverage() {
        return average;
    }

    //Zeros out current value and starts new counter interval.
    public double startNewInterval() {

        long count = current.getAndSet(0);
        this.average = this.average == 0 ? count : (this.coefficient * count) + ((1 - this.coefficient) * this.average);
        return this.average;

    }
}
