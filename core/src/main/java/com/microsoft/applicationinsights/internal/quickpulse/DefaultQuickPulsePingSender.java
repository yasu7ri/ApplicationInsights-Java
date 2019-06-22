/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.quickpulse;

import java.io.IOException;
import java.util.Date;

import com.microsoft.applicationinsights.EndpointConfiguration;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.internal.channel.common.ApacheSender;

/**
 * Created by gupele on 12/12/2016.
 */
final class DefaultQuickPulsePingSender implements QuickPulsePingSender {

    private final String quickPulsePingUri;
    private final ApacheSender apacheSender;
    private final QuickPulseNetworkHelper networkHelper = new QuickPulseNetworkHelper();
    private String pingPrefix;
    private long lastValidTransmission = 0;

    /**
     * @deprecated Instead, use {@link #DefaultQuickPulsePingSender(ApacheSender, String, String, String, EndpointConfiguration)} )}
     */
    @Deprecated
    public DefaultQuickPulsePingSender(final ApacheSender apacheSender, final String instanceName, final String quickPulseId) {
        this(apacheSender, instanceName, quickPulseId, TelemetryConfiguration.getActive().getInstrumentationKey(),
                TelemetryConfiguration.getActive().getEndpoints());
    }
    public DefaultQuickPulsePingSender(final ApacheSender apacheSender, final String instanceName, final String quickPulseId,
                                       String instrumentationKey, EndpointConfiguration endpoints) {
        this.apacheSender = apacheSender;

        quickPulsePingUri = endpoints.getQuickPulseEndpoint() + "/ping?ikey=" + instrumentationKey;

        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"Documents\": null,");
        sb.append("\"Instance\":\"" + instanceName + "\",");
        sb.append("\"InstrumentationKey\": null,");
        sb.append("\"InvariantVersion\": 2,");
        sb.append("\"MachineName\":\"" + instanceName + "\",");
        sb.append("\"Metrics\": null,");
        sb.append("\"StreamId\": \"" + quickPulseId + "\",");
        sb.append("\"Timestamp\": \"\\/Date(");

        pingPrefix = sb.toString();
    }

    @Override
    public QuickPulseStatus ping() {
        final Date currentDate = new Date();
        final HttpPost request = networkHelper.buildRequest(currentDate, quickPulsePingUri);

        final ByteArrayEntity pingEntity = buildPingEntity(currentDate.getTime());
        request.setEntity(pingEntity);

        final long sendTime = System.nanoTime();
        HttpResponse response = null;
        try {
            response = apacheSender.sendPostRequest(request);
            if (networkHelper.isSuccess(response)) {
                final QuickPulseStatus quickPulseResultStatus = networkHelper.getQuickPulseStatus(response);
                switch (quickPulseResultStatus) {
                case QP_IS_OFF:
                case QP_IS_ON:
                    lastValidTransmission = sendTime;
                    return quickPulseResultStatus;

                case ERROR:
                    break;

                default:
                    break;
                }
            }
        } catch (IOException e) {

        } finally {
            if (response != null) {
                apacheSender.dispose(response);
            }
        }
        return onPingError(sendTime);
    }

    private ByteArrayEntity buildPingEntity(long timeInMillis) {

        StringBuilder sb = new StringBuilder(pingPrefix);
        sb.append(timeInMillis);
        sb.append(")\\/\",");
        sb.append("\"Version\":\"2.2.0-738\"");
        sb.append("}");
        ByteArrayEntity bae = new ByteArrayEntity(sb.toString().getBytes());
        return bae;
    }

    private QuickPulseStatus onPingError(long sendTime) {
        final double timeFromLastValidTransmission = (sendTime - lastValidTransmission) / 1000000000.0;
        if (timeFromLastValidTransmission >= 60.0) {
            return QuickPulseStatus.ERROR;
        }

        return QuickPulseStatus.QP_IS_OFF;
    }
}
