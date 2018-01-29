package com.microsoft.applicationinsights.internal.channel.samplingV2;

import com.microsoft.applicationinsights.extensibility.TelemetryProcessor;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telemetry processor to estimate ideal sampling percentage
 */
public class SamplingPercentageEstimatorTelemetryProcessor implements TelemetryProcessor{

    /**
     * Dynamic sampling estimator settings
     */
    private SamplingPercentageEstimatorSettings settings;

    /**
     * Average telemetry item counter
     */
    private ExponentialMovingAverageCounter itemCount;

    /**
     * Thread Executor to start timed task
     */
    private ScheduledExecutorService evaluationTimer;

    /**
     * Current evaluation interval
     */
    private long evaluationInterval;

    /**
     * Current Sampling Rate
     */
    private int currentSamplingRate;

    /**
     * Last Time instance when sampling rate was modified
     */
    private Date samplingPercentageLastChangedDateTime;

    /**
     *   Interface acting as callback to invoke everytime sampling percentage is evaluated.
     */
    private AdaptiveSamplingPercentageEvaluatedCallback evaluationCallback;

    /**
     * Initializes a new instance of SamplingPercentageEstimatorTelemetryProcessor
     * @param settings Dynamic Sampling Percentage Estimator Settings
     * @param evaluationCallback The Interface used for callaback everytime sampling percentage is evaluated
     */
    public SamplingPercentageEstimatorTelemetryProcessor(SamplingPercentageEstimatorSettings settings,
                                                         AdaptiveSamplingPercentageEvaluatedCallback evaluationCallback) {

        if (settings == null) {
            throw new IllegalArgumentException("Settings not present");
        }

        this.evaluationCallback = evaluationCallback;
        this.settings = settings;

        this.currentSamplingRate = settings.getEffectiveInitialSamplingRate();
        this.itemCount = new ExponentialMovingAverageCounter(settings.getEffectiveMovingAverageRation());

        this.samplingPercentageLastChangedDateTime = new Date();

        this.evaluationInterval = settings.getEffectiveEvaluationInterval();

        this.evaluationTimer = Executors.newScheduledThreadPool(1);
        evaluationTimer.scheduleAtFixedRate(new EstimateSamplingPercentage(), evaluationInterval, evaluationInterval, TimeUnit.SECONDS);


    }

    /**
     * Process telemetry item, in this case we increment our counter for each telemetry item.
     * @param telemetry
     * @return True
     */
    @Override
    public boolean process(Telemetry telemetry) {

        this.itemCount.increment();

        return true;
    }


    /**
     * Checks to see if exponential moving average is changed
     * @param running Currently running value of moving average
     * @param current Value set in the algorithm parameter
     * @return True if value is changed
     */
    private static boolean movingAverageCoefficientChanged(double running, double current) {

        final double precision = 1e-12;
        return (running < current - precision) || (running > current + precision);
    }

    /**
     * Runnable callback which is executed by the scheduled timer for evaluation
     */
     private class EstimateSamplingPercentage implements Runnable {


        @Override
        public void run() {

            //get observed after sample eps
            double observedEps = itemCount.startNewInterval() / evaluationInterval;

            //we see events post sampling, so get pre-sampling eps
            double beforeSamplingEps = observedEps * currentSamplingRate;

            //calculate the suggested sampling rate
            int suggestedSamplingRate = (int) Math.ceil(beforeSamplingEps / settings.getEffectiveMaxTelemetryItemsPerSecond());

            //adjust the suggested sampling rate so that it fits between min and max
            if (suggestedSamplingRate > settings.getEffectiveMaxSamplingRate()) {
                suggestedSamplingRate = settings.getEffectiveMaxSamplingRate();
            }

            if (suggestedSamplingRate < settings.getEffectiveMinSamplingRate()) {
                suggestedSamplingRate = settings.getEffectiveMinSamplingRate();
            }

            //check to see if sampling percentage change is needed
            boolean samplingPercentageChangeNeeded = suggestedSamplingRate != currentSamplingRate;

            //TODO: if evaluation interval has changed

            if (samplingPercentageChangeNeeded) {

                //Check if minimum time has passed to vary the sampling rate
                if ((new Date().getTime() - samplingPercentageLastChangedDateTime.getTime()) <
                        (suggestedSamplingRate > currentSamplingRate ?
                        settings.getEffectiveSamplingPercentageDecreaseTimeout() :
                        settings.getEffectiveSamplingPercentageIncreaseTimeout())) {}

                        samplingPercentageChangeNeeded = false;

            }

            //call the evaluation callback if provided
            if (evaluationCallback != null) {

                try {
                    evaluationCallback.invoke(observedEps, 100 / currentSamplingRate, 100 / suggestedSamplingRate,
                            samplingPercentageChangeNeeded, settings);
                }
                catch (Exception e) {
                    InternalLogger.INSTANCE.error("Sampling call back failed. Stack Trace is %s", ExceptionUtils.getStackTrace(e));
                }
            }


            if (samplingPercentageChangeNeeded) {

                //Apply sampling rate change
                samplingPercentageLastChangedDateTime = new Date();
                currentSamplingRate = suggestedSamplingRate;
            }

            if (samplingPercentageChangeNeeded || movingAverageCoefficientChanged(itemCount.getCoefficient(), settings.getEffectiveMovingAverageRation())) {

                //Since we are observing event count post sampling and as we are about to change
                // the sampling rate or change the coefficient let's reset the counter
                itemCount = new ExponentialMovingAverageCounter(settings.getEffectiveMovingAverageRation());
            }

        }
    }


}
