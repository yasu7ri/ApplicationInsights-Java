package com.microsoft.applicationinsights.extensibility.initializer;


import org.junit.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestAggregatorTelemetryInitializerTests {

    @Test
    public void testFindReplace() {
        String spec = "/api/v1/sub/{subId}/resource/{resId}/create";
        Pattern p = Pattern.compile("[{](.*?)[}]");
        Matcher m = p.matcher(spec);
        StringBuffer sb = new StringBuffer();
        Map<Integer, String> names = new HashMap<Integer, String>();
        for (int i = 1; m.find(); i++) {
            final String paramName = m.group(1);
            System.out.printf("%d: %s%n", i, paramName);
            m.appendReplacement(sb, Matcher.quoteReplacement("(.*?)"));
            names.put(i, paramName);
        }
        m.appendTail(sb);
        sb.insert(0, ".*").append(".*");
        String urlPattern = sb.toString();
        urlPattern = urlPattern.replaceAll("\\/", Matcher.quoteReplacement("\\/"));
        System.out.println(urlPattern);

        String url = "http://api.host.com/api/v1/sub/1234-abcd-ef/resource/some-res/create?q=asdf";
        Pattern up = Pattern.compile(urlPattern);
        Matcher um = up.matcher(url);
        Assert.assertTrue("didn't match", um.matches());
        StringBuffer sb2 = new StringBuffer();
        int lastIndex = 0;
        for (Entry<Integer, String> entry : names.entrySet()) {
            final Integer gn = entry.getKey();
            String g = um.group(gn);
            final String pname = entry.getValue();
            System.out.printf("%d: %s = %s%n", gn, pname, g);
            sb2.append(url.substring(lastIndex, um.start(gn)))
                    .append('{')
                    .append(pname)
                    .append('}');
            lastIndex = um.end(gn);
        }
        sb2.append(url.substring(lastIndex));
        System.out.println(sb2.toString());
    }

    @Test
    public void generateUriParameterExtractionRegexReturnsRegexAndExtractsParameters() {
        throw new UnsupportedOperationException("not implemented");
    }
}
