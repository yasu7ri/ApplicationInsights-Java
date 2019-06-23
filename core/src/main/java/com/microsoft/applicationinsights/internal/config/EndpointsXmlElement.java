package com.microsoft.applicationinsights.internal.config;

import javax.xml.bind.annotation.XmlElement;

public class EndpointsXmlElement {
    private String telemetry;
    private String quickPulse;

    public String getTelemetry() {
        return telemetry;
    }

    @XmlElement(name = "Telemetry")
    public void setTelemetry(String telemetry) {
        this.telemetry = telemetry;
    }

    public String getQuickPulse() {
        return quickPulse;
    }

    @XmlElement(name = "QuickPulse")
    public void setQuickPulse(String quickPulse) {
        this.quickPulse = quickPulse;
    }
}
