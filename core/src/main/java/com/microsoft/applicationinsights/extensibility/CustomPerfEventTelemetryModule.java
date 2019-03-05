package com.microsoft.applicationinsights.extensibility;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import com.microsoft.applicationinsights.internal.perfcounter.CpuPerformanceCounterCalculator;
import com.microsoft.applicationinsights.internal.shutdown.SDKShutdownActivity;
import com.microsoft.applicationinsights.internal.util.ThreadPoolUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CustomPerfEventTelemetryModule implements TelemetryModule {

    private final ScheduledExecutorService executor;
    private final CpuPerformanceCounterCalculator cpuCalculator;
    private final MemoryMXBean memoryBean;
    private final TelemetryClient telemetry;

    private static final String EVENT_NAME = "CustomPerfEvent";

    public CustomPerfEventTelemetryModule() {
        executor = Executors.newSingleThreadScheduledExecutor(ThreadPoolUtils.createNamedDaemonThreadFactory("CustomPerfDataCollector"));
        SDKShutdownActivity.INSTANCE.register(executor);
        cpuCalculator = new CpuPerformanceCounterCalculator();
        memoryBean = ManagementFactory.getMemoryMXBean();
        telemetry = new TelemetryClient();
    }

    @Override
    public void initialize(TelemetryConfiguration configuration) {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Map<String, Double> metrics = new HashMap<>();
                final Double processCpuUsage = cpuCalculator.getProcessCpuUsage();
                final Double memoryValue = Double.valueOf(memoryBean.getHeapMemoryUsage().getCommitted());

                metrics.put("Cpu", processCpuUsage);
                metrics.put("Mem", memoryValue);
                telemetry.trackEvent(EVENT_NAME, null, metrics);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
}
