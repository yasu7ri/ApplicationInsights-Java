package com.microsoft.applicationinsights.extensibility.initializer;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestAggregatorTelemetryInitializer implements TelemetryInitializer {

    static class UriParameterDescriptor {
        private String name;
        private int group = -1;

        public UriParameterDescriptor() {
        }

        public UriParameterDescriptor(String name, int group) {
            this.name = name;
            this.group = group;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getGroup() {
            return group;
        }

        public void setGroup(int group) {
            this.group = group;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UriParameterDescriptor that = (UriParameterDescriptor) o;
            return getGroup() == that.getGroup() &&
                    Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName(), getGroup());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UriParameterDescriptor{");
            sb.append("name='").append(name).append('\'');
            sb.append(", group=").append(group);
            sb.append('}');
            return sb.toString();
        }
    }

    static class UriFilterDescriptor {
        private String name;
        private String fullUriSpec;
        private Pattern regex;

        public UriFilterDescriptor(String name, String fullUriSpec) {
            this.name = name;
            this.fullUriSpec = fullUriSpec;
        }

        public UriFilterDescriptor() {

        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullUriSpec() {
            return fullUriSpec;
        }

        public void setFullUriSpec(String fullUriSpec) {
            this.fullUriSpec = fullUriSpec;
        }

        public Pattern getRegex() {
            return regex;
        }

        public void setRegex(Pattern regex) {
            this.regex = regex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UriFilterDescriptor that = (UriFilterDescriptor) o;
            return Objects.equals(getName(), that.getName()) &&
                    Objects.equals(getFullUriSpec(), that.getFullUriSpec());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName(), getFullUriSpec());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UriFilterDescriptor{");
            sb.append("name='").append(name).append('\'');
            sb.append(", fullUriSpec='").append(fullUriSpec).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    Multimap<UriFilterDescriptor, UriParameterDescriptor> patternMap = ListMultimapBuilder
            .linkedHashKeys()
            .arrayListValues()
            .build();

    public RequestAggregatorTelemetryInitializer() {
        this(null);
    }

    private static Pattern parameterExtracterPattern = Pattern.compile("\\{(.*?)\\}");

    public RequestAggregatorTelemetryInitializer(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException(RequestAggregatorTelemetryInitializer.class.getSimpleName()+": You must specify at least one parameter");
        }

        for(Entry<String, String> entry : data.entrySet()) {
            UriFilterDescriptor descriptor = new UriFilterDescriptor();
            descriptor.setName(entry.getKey());
            String spec = entry.getValue();
            descriptor.setFullUriSpec(spec);
            List<UriParameterDescriptor> params = new ArrayList<UriParameterDescriptor>();
            String regex = generateUriParameterExtractionRegex(spec, params);
            descriptor.setRegex(Pattern.compile(regex));
            patternMap.putAll(descriptor, params);
        }
    }

    static String generateUriParameterExtractionRegex(String uriSpec, List<UriParameterDescriptor> params) {
        Matcher specMatcher = parameterExtracterPattern.matcher(uriSpec);
        StringBuffer regexBuffer = new StringBuffer();
        for (int i = 1; specMatcher.find(); i++) {
            UriParameterDescriptor paramSpec = new UriParameterDescriptor();
            String paramName = specMatcher.group(1);
            paramSpec.setName(paramName);
            paramSpec.setGroup(i);
            params.add(paramSpec);
            specMatcher.appendReplacement(regexBuffer, Matcher.quoteReplacement("(.*?)"));
        }
        specMatcher.appendTail(regexBuffer);
        return regexBuffer.append(".*").insert(0, ".*").toString().replaceAll("\\/", Matcher.quoteReplacement("\\/"));
    }



    @Override
    public void initialize(Telemetry telemetry) {
        if (!(telemetry instanceof RequestTelemetry)) {
            return;
        }

        RequestTelemetry rt = (RequestTelemetry) telemetry;
        String rname = rt.getName(); // should be in the form GET /some/uri
        for (UriFilterDescriptor ufd : patternMap.keySet()) {
            Matcher m = ufd.getRegex().matcher(rname);
            if (m.matches()) {
                StringBuffer sb = new StringBuffer();
                int lastIndex = 0;
                for (UriParameterDescriptor upd : patternMap.get(ufd)) {
                    final int groupNumber = upd.getGroup();
                    String value = m.group(groupNumber);
                    rt.getProperties().put(upd.getName(), value);
                    sb.append(rname, lastIndex, m.start(groupNumber))
                            .append("{")
                            .append(upd.getName())
                            .append("}");
                    lastIndex = m.end(groupNumber);
                }
                sb.append(rname.substring(lastIndex));
                rt.setName(sb.toString());
                break;
            }
        }
    }
}
