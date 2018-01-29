package com.microsoft.applicationinsights.internal.channel.samplingV2;

public interface AdaptiveSamplingPercentageEvaluatedCallback {

     void invoke(double afterSamplingTelemetryItemRatePerSecond,
                       double currentSamplingPercentage,
                       double newSamplingPercentage,
                       boolean isSamplingPercentageChanged,
                       SamplingPercentageEstimatorSettings settings);
}
