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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * A handler for a jetty webserver to handle classes annotated
 * as WebService.
 *
 * @author Florian Frankenberger
 */
public class WebServiceHandler<T> extends AbstractHandler {

    private static final Logger LOGGER = Logger.getLogger(WebServiceHandler.class.getCanonicalName());

    private static final String PARAM_PATTERN = "\\{(.*?)\\}";
    private static final String PARAM_MATCH_PATTERN = "([^\\/]+?)";
    private static final Pattern URL_PATTERN = Pattern.compile(PARAM_PATTERN);

    public final T service;
    private final String pathSuffix;

    private final List<Mapping> methodMappings = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebServiceHandler(T service) {
        this.service = service;
        final Class<? extends Object> serviceClass = service.getClass();

        if (serviceClass.isAnnotationPresent(WebService.class)) {
            pathSuffix = serviceClass.getAnnotation(WebService.class).value();

            for (Method method : serviceClass.getMethods()) {
                if (method.isAnnotationPresent(GetMapping.class)) {
                    Mapping mapping = prepareMapping(pathSuffix, method);

                    LOGGER.log(Level.INFO, "Registering [{0}] for {1}.{2}()",
                            new Object[]{mapping.getPath(), serviceClass.getSimpleName(), method.getName()});
                    methodMappings.add(mapping);
                }
            }
        } else {
            throw new IllegalArgumentException("no webservice descriptor present at class "
                    + serviceClass.getCanonicalName());
        }
    }

    @Override
    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {

        //only check mapping if path suffix matches
        if (target.startsWith(pathSuffix)) {
            for (Mapping mapping : this.methodMappings) {
                Matcher matcher = mapping.getPattern().matcher(target);
                if (matcher.matches()) {
                    baseRequest.setHandled(true);

                    try {
                        final Object[] parameters = mapping.formatParameters(matcher);
                        final Object result = mapping.getMethod().invoke(service, parameters);
                        if (result != null) {
                            response.setContentType("application/json; charset=utf-8");
                            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                            objectMapper.writeValue(response.getOutputStream(), result);
                        }
                        response.setStatus(HttpServletResponse.SC_OK);
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "Could not execute method " + mapping.getMethod(), t);
                        response.sendError(400);
                    }
                }
            }
        }
    }

    private static Mapping prepareMapping(String pathSuffix, Method method) {
        final GetMapping getMapping = method.getAnnotation(GetMapping.class);
        final String rawPattern = getMapping.value();
        final String path = pathSuffix + rawPattern;

        //prepare parameter map
        final Map<String, Integer> parameterMap = new HashMap<>();
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            if (parameters[i].isAnnotationPresent(GetParameter.class)) {
                parameterMap.put(parameters[i].getAnnotation(GetParameter.class).value(), i);
            }
        }

        //prepare url pattern
        final Map<Integer, Integer> parameterMapping = new HashMap<>();

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
        StringBuilder pattern = new StringBuilder();
        pattern.append('^');
        pattern.append(Pattern.quote(pathSuffix));
        boolean first = true;
        for (String str : rawPattern.split(PARAM_PATTERN)) {
            if (first) {
                first = false;
            } else {
                pattern.append(PARAM_MATCH_PATTERN);
            }
            pattern.append(Pattern.quote(str));
        }
        if (endsWithPattern) {
            pattern.append(PARAM_MATCH_PATTERN);
        }
        pattern.append('$');

        return new Mapping(path, method, parameterMapping, Pattern.compile(pattern.toString()));
    }

    private static class Mapping {

        private final String path;
        private final Method method;
        private final Map<Integer, Integer> parameterMapping;
        private final Pattern pattern;

        public Mapping(String path, Method method, Map<Integer, Integer> parameterMapping, Pattern pattern) {
            this.path = path;
            this.method = method;
            this.parameterMapping = parameterMapping;
            this.pattern = pattern;
        }

        public String getPath() {
            return path;
        }

        public Method getMethod() {
            return method;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public Object[] formatParameters(Matcher matcher) {
            Object[] result = new Object[method.getParameterCount()];
            Parameter[] parameters = method.getParameters();

            for (Entry<Integer, Integer> entry : parameterMapping.entrySet()) {
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
                                        throw new IllegalStateException("Can't map value \"" + raw
                                                + "\" to type " + type.getCanonicalName());
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

    }

}
