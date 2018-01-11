package com.microsoft.applicationinsights.internal.channel.samplingV2;

import java.util.concurrent.TimeUnit;

public class SamplingPercentageEstimatorSettings {

    public double maxTelemetryItemsPerSecond;
    public double initialSamplingPercentage;
    public double minSamplingPercentage;
    public double maxSamplingPercentage;
    public long evaluationInterval;
    public long samplingPercentageDecreaseTimeOut;
    public long samplingPercentageIncreaseTimeout;
    public double movingAverageRatio;


    public SamplingPercentageEstimatorSettings() {

        //Setting default values
        this.maxTelemetryItemsPerSecond = 5.0;
        this.initialSamplingPercentage = 100.0;
        this.minSamplingPercentage = 0.1;
        this.maxSamplingPercentage = 100.0;
        this.evaluationInterval = TimeUnit.SECONDS.toSeconds(15);
        this.samplingPercentageDecreaseTimeOut = TimeUnit.MINUTES.toMinutes(2);
        this.samplingPercentageIncreaseTimeout = TimeUnit.MINUTES.toMinutes(15);
        this.movingAverageRatio = 0.25;
    }

    public double getMaxTelemetryItemsPerSecond() {
        return maxTelemetryItemsPerSecond;
    }

    public void setMaxTelemetryItemsPerSecond(double maxTelemetryItemsPerSecond) {
        this.maxTelemetryItemsPerSecond = maxTelemetryItemsPerSecond;
    }

    public double getInitialSamplingPercentage() {
        return initialSamplingPercentage;
    }

    public void setInitialSamplingPercentage(double initialSamplingPercentage) {
        this.initialSamplingPercentage = initialSamplingPercentage;
    }

    public double getMinSamplingPercentage() {
        return minSamplingPercentage;
    }

    public void setMinSamplingPercentage(double minSamplingPercentage) {
        this.minSamplingPercentage = minSamplingPercentage;
    }

    public double getMaxSamplingPercentage() {
        return maxSamplingPercentage;
    }

    public void setMaxSamplingPercentage(double maxSamplingPercentage) {
        this.maxSamplingPercentage = maxSamplingPercentage;
    }

    public long getEvaluationInterval() {
        return evaluationInterval;
    }

    public void setEvaluationInterval(long evaluationInterval) {
        this.evaluationInterval = evaluationInterval;
    }

    public long getSamplingPercentageDecreaseTimeOut() {
        return samplingPercentageDecreaseTimeOut;
    }

    public void setSamplingPercentageDecreaseTimeOut(long samplingPercentageDecreaseTimeOut) {
        this.samplingPercentageDecreaseTimeOut = samplingPercentageDecreaseTimeOut;
    }

    public long getSamplingPercentageIncreaseTimeout() {
        return samplingPercentageIncreaseTimeout;
    }

    public void setSamplingPercentageIncreaseTimeout(long samplingPercentageIncreaseTimeout) {
        this.samplingPercentageIncreaseTimeout = samplingPercentageIncreaseTimeout;
    }

    public double getMovingAverageRatio() {
        return movingAverageRatio;
    }

    public void setMovingAverageRatio(double movingAverageRatio) {
        this.movingAverageRatio = movingAverageRatio;
    }

    /**
     * Gets effective telemetry items per rate per second
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    double getEffectiveTelemetryItemsPerSecond() {
        return this.maxTelemetryItemsPerSecond <= 0 ? 1e-12 : this.maxTelemetryItemsPerSecond;
    }

    /**
     * Gets effective initial sampling rate
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    int getEffectiveInitialSamplingRate() {
        return (int) Math.floor(100 / adjustSamplingPercentage(this.initialSamplingPercentage));
    }

    /**
     * Gets effective minimum sampling rate
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    int getEffectiveMinSamplingRate() {
        return (int) Math.floor(100 / adjustSamplingPercentage(this.minSamplingPercentage));
    }

    /**
     * Gets effective maximum sampling rate
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    int getEffectiveMaxSamplingRate() {
        return (int) Math.floor(100 / adjustSamplingPercentage(this.maxSamplingPercentage));
    }

    /**
     * Gets effective Sampling percentage evaluation interval
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    long getEffectiveEvaluationInterval() {
        return this.evaluationInterval == TimeUnit.SECONDS.toSeconds(0) ? TimeUnit.SECONDS.toSeconds(0) : this.evaluationInterval;
    }

    /**
     * Gets effective sampling percentage decrease timeout
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    long getEffectiveSamplingPercentageDecreaseTimeout() {
        return this.samplingPercentageDecreaseTimeOut == TimeUnit.SECONDS.toSeconds(0) ? TimeUnit.MINUTES.toMinutes(2)
                : this.samplingPercentageDecreaseTimeOut;
    }

    /**
     * Gets effective sampling percentage increase timeout
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    long getEffectiveSamplingPercentageIncreaseTimeout() {
        return this.samplingPercentageIncreaseTimeout == TimeUnit.SECONDS.toSeconds(0) ? TimeUnit.MINUTES.toMinutes(15)
                : this.samplingPercentageIncreaseTimeout;
    }

    /**
     * Gets effective exponential moving average ratio
     * adjusted in case user makes error while setting the value
     *
     * @return
     */
    double getEffectiveMovingAverageRation() {
        return this.movingAverageRatio < 0 ? 0.25 : this.movingAverageRatio;
    }

    /**
     * Adjusts sampling percentage set by user to account for zeros and above 100%
     *
     * @param samplingPercentage
     * @return
     */
    private static double adjustSamplingPercentage(double samplingPercentage) {
        return samplingPercentage > 100 ? 100 : samplingPercentage <= 0 ? 1e-6 : samplingPercentage;
    }

}
