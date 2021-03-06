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

package com.microsoft.applicationinsights.telemetry;

import com.microsoft.applicationinsights.internal.schemav2.Data;
import com.microsoft.applicationinsights.internal.schemav2.Domain;
import com.microsoft.applicationinsights.internal.schemav2.Envelope;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;
import com.microsoft.applicationinsights.internal.util.Sanitizer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

/**
 * Superclass for all telemetry data classes.
 */
public abstract class BaseTelemetry<T extends Domain> implements Telemetry {
    private TelemetryContext context;
    private Date timestamp;
    private String sequence;

    public static final String TELEMETRY_NAME_PREFIX = "Microsoft.ApplicationInsights.";

    protected BaseTelemetry() {
    }

    /**
     * Initializes the instance with the context properties
     *
     * @param properties The context properties
     */
    protected void initialize(ConcurrentMap<String, String> properties) {
        this.context = new TelemetryContext(properties, new ContextTagsMap());
    }

    public abstract int getVer();

    /**
     * Sequence field used to track absolute order of uploaded events.
     * It is a two-part value that includes a stable identifier for the current boot
     * session and an incrementing identifier for each event added to the upload queue
     * <p>
     * The Sequence helps track how many events were fired and how many events were uploaded and
     * enables identification of data lost during upload and de-duplication of events on the ingress server.
     * <p>
     * Gets the value that defines absolute order of the telemetry item.
     *
     * @return The sequence of the Telemetry.
     */
    @Override
    public String getSequence() {
        return sequence;
    }

    /**
     * Sets the value that defines absolute order of the telemetry item.
     *
     * @param sequence The sequence of the Telemetry.
     */
    @Override
    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    /**
     * Gets date and time when event was recorded.
     *
     * @return The timestamp as Date
     */
    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets date and time when event was recorded.
     *
     * @param date The timestamp as Date.
     */
    @Override
    public void setTimestamp(Date date) {
        timestamp = date;
    }

    /**
     * Gets the context associated with the current telemetry item.
     *
     * @return The context
     */
    @Override
    public TelemetryContext getContext() {
        return context;
    }

    /**
     * Gets a dictionary of application-defined property names and values providing additional information about this event.
     *
     * @return The properties
     */
    @Override
    public Map<String, String> getProperties() {
        return this.context.getProperties();
    }

    /**
     * @deprecated
     * Makes sure the data to send is sanitized from bad chars, proper length etc.
     */
    @Override
    @Deprecated
    public void sanitize() {
        Sanitizer.sanitizeProperties(this.getProperties());
        additionalSanitize();
    }

    /**
     * Serializes this object in JSON format.
     *
     * @param writer The writer that helps with serializing into Json format
     * @throws IOException The exception that might be thrown during the serialization
     */
    @Override
    public void serialize(JsonTelemetryDataSerializer writer) throws IOException {

        String telemetryName = getTelemetryName(normalizeInstrumentationKey(context.getInstrumentationKey()), this.getEnvelopName());

        Envelope envelope = new Envelope();
        envelope.setName(telemetryName);

        setSampleRate(envelope);
        envelope.setIKey(context.getInstrumentationKey());
        envelope.setSeq(sequence);
        Data<T> tmp = new Data<T>();
        tmp.setBaseData(getData());
        tmp.setBaseType(this.getBaseTypeName());
        envelope.setData(tmp);
        if (getTimestamp() != null) envelope.setTime(LocalStringsUtils.getDateFormatter().format(getTimestamp()));
        envelope.setTags(context.getTags());

        envelope.serialize(writer);
    }

    /**
     * THIS IS FOR DEBUGGING AND TESTING ONLY!
     * DON'T USE THIS IN HAPPY-PATH, PRODUCTION CODE.
     *
     * @return Json representation of this telemetry item.
     */
    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            JsonTelemetryDataSerializer jtds = new JsonTelemetryDataSerializer(sw);
            this.serialize(jtds);
            jtds.close();
            return sw.toString();
        } catch (IOException e) {
            // shouldn't happen with a string writer
            throw new RuntimeException("Error serializing "+this.getClass().getSimpleName()+" toString", e);
        }
    }

    @Override
    public void reset() {
    }

    /**
     * Concrete classes should implement this method
     */
    @Deprecated
    protected abstract void additionalSanitize();

    /**
     * Concrete classes should implement this method which supplies the
     * data structure that this instance works with, which needs to implement {@link JsonSerializable}
     *
     * @return The inner data structure
     */
    protected abstract T getData();

    protected void setSampleRate(Envelope envelope) {
    }

    public String getEnvelopName() {
        throw new UnsupportedOperationException();
    }

    public String getBaseTypeName() {
        throw new UnsupportedOperationException();
    }

    public static String normalizeInstrumentationKey(String instrumentationKey){
        if (StringUtils.isEmpty(instrumentationKey) || StringUtils.containsOnly(instrumentationKey, ".- ")){
            return "";
        }
        else{
            return instrumentationKey.replace("-", "").toLowerCase() + ".";
        }
    }

    public static String getTelemetryName(String normalizedInstrumentationKey, String envelopType){
        return String.format(
                "%s%s%s",
                TELEMETRY_NAME_PREFIX,
                normalizedInstrumentationKey,
                envelopType
                );
    }

}
