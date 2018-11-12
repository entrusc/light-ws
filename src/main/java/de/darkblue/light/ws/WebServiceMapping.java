/*
 * The MIT License
 *
 * Copyright 2018 Florian Frankenberger.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.darkblue.light.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Florian Frankenberger
 */
abstract class WebServiceMapping {

    private static final Logger LOGGER = Logger.getLogger(WebServiceMapping.class.getCanonicalName());
    private static final ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();
    private static final String PARAM_PATTERN = "\\{(.*?)\\}";
    private static final String PARAM_MATCH_PATTERN = "([^\\/]+?)";
    private static final Pattern URL_PATTERN = Pattern.compile(PARAM_PATTERN);

    private String path;
    private Pattern pattern;

    private final HttpMethod httpMethod;

    private final Method method;
    private final Map<Integer, Integer> parameterMapping = new HashMap<>();

    public WebServiceMapping(HttpMethod httpMethod, String pathSuffix,
            Method method) {
        initMapping(pathSuffix, method);
        this.httpMethod = httpMethod;
        this.method = method;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Method getMethod() {
        return method;
    }

    public Map<Integer, Integer> getParameterMapping() {
        return parameterMapping;
    }

    public boolean execute(Object service, String target,
            HttpServletRequest request, HttpServletResponse response) {
        if (getHttpMethod().matches(request.getMethod())) {
            Matcher matcher = getPattern().matcher(target);
            if (matcher.matches()) {
                try {
                    final Object[] parameters = formatPathParameters(matcher);
                    addParameters(request, parameters);
                    final Object result = getMethod().invoke(service, parameters);
                    if (result != null) {
                        response.setContentType("application/json; charset=utf-8");
                        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        OBJECT_WRITER.writeValue(response.getOutputStream(), result);
                    }
                    response.setStatus(HttpServletResponse.SC_OK);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Could not execute method " + getMethod(), t);
                    response.sendError(400);
                } finally {
                    return true;
                }
            }
        }
        return false;
    }

    protected abstract void addParameters(HttpServletRequest request, Object[] parameters);

    private Object[] formatPathParameters(Matcher matcher) {
        Object[] result = new Object[method.getParameterCount()];
        Parameter[] parameters = method.getParameters();
        for (Map.Entry<Integer, Integer> entry : parameterMapping.entrySet()) {
            final int groupId = entry.getKey();
            final int parameterId = entry.getValue();
            final Parameter parameter = parameters[parameterId];
            final String raw = matcher.group(groupId);
            final Class<?> type = parameter.getType();
            final Object value;
            if (type == Integer.class || type == int.class) {
                value = Integer.valueOf(raw);
            } else {
                if (type == Long.class || type == long.class) {
                    value = Long.valueOf(raw);
                } else {
                    if (type == Float.class || type == float.class) {
                        value = Float.valueOf(raw);
                    } else {
                        if (type == Double.class || type == double.class) {
                            value = Double.valueOf(raw);
                        } else {
                            if (type == Boolean.class || type == boolean.class) {
                                value = Boolean.valueOf(raw);
                            } else {
                                if (type == String.class) {
                                    value = raw;
                                } else {
                                    throw new IllegalStateException("Can't map value \""
                                            + raw + "\" to type " + type.getCanonicalName());
                                }
                            }
                        }
                    }
                }
            }
            result[parameterId] = value;
        }
        return result;
    }

    private void initMapping(String pathSuffix, Method method) {
        String rawPattern = null;
        if (method.isAnnotationPresent(GetMapping.class)) {
            rawPattern = method.getAnnotation(GetMapping.class).value();
        } else
            if (method.isAnnotationPresent(PostMapping.class)) {
                rawPattern = method.getAnnotation(PostMapping.class).value();
            }

        if (rawPattern == null) {
            throw new IllegalStateException("Given method " + method + " has "
                    + "neither a GetMapping nor a PostMapping annotation");
        }
        path = pathSuffix + rawPattern;

        //prepare parameter map
        final Map<String, Integer> parameterMap = new HashMap<>();
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            if (parameters[i].isAnnotationPresent(GetParameter.class)) {
                parameterMap.put(parameters[i].getAnnotation(GetParameter.class).value(), i);
            }
        }

        //prepare url pattern
        parameterMapping.clear();

        Matcher matcher = URL_PATTERN.matcher(rawPattern);
        int counter = 0;
        while (matcher.find()) {
            final String getParameter = matcher.group(1);
            if (parameterMap.containsKey(getParameter)) {
                parameterMapping.put(++counter, parameterMap.get(getParameter));
            } else {
                throw new IllegalStateException("Parameter " + getParameter
                        + " was not found in method " + method);
            }
        }

        //build url pattern
        boolean endsWithPattern = rawPattern.endsWith("}");
        StringBuilder patternStr = new StringBuilder();
        patternStr.append('^');
        patternStr.append(Pattern.quote(pathSuffix));
        boolean first = true;
        for (String str : rawPattern.split(PARAM_PATTERN)) {
            if (first) {
                first = false;
            } else {
                patternStr.append(PARAM_MATCH_PATTERN);
            }
            patternStr.append(Pattern.quote(str));
        }
        if (endsWithPattern) {
            patternStr.append(PARAM_MATCH_PATTERN);
        }
        patternStr.append('$');

        this.pattern = Pattern.compile(patternStr.toString());
    }

}
