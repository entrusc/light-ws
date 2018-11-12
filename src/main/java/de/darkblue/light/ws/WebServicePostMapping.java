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
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServicePostMapping extends WebServiceMapping {

    private static final ObjectReader OBJECT_READER = new ObjectMapper().reader();

    private Class<?> postParamType;
    private int postParamPos;

    public WebServicePostMapping(String pathSuffix, Method method) {
        super(HttpMethod.POST, pathSuffix, method);
        initPostParameter();
    }

    private void initPostParameter() {
        Method method = getMethod();
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            final Parameter param = parameters[i];
            if (param.isAnnotationPresent(PostParameter.class)) {
                postParamPos = i;
                postParamType = param.getType();
                return; //we take the first best post param (there shoud not be more than one!)
            }
        }
        throw new IllegalStateException("no post parameter declared in method " + getMethod());
    }

    @Override
    protected void addParameters(HttpServletRequest request, Object[] parameters) {
        if (request.getContentType().trim().toLowerCase().startsWith("application/json")) {
            try (Reader reader = request.getReader()) {
                Object postParameter = OBJECT_READER.forType(postParamType).readValue(reader);
                parameters[postParamPos] = postParameter;
            } catch (IOException ex) {
                throw new IllegalArgumentException("Given post parameter could "
                        + "not be converted to type " + postParamType, ex);
            }
        } else {
            throw new IllegalArgumentException("Given post parameter was of type \"" + request.getContentType()
                    + "\" and not of type application/json");
        }
    }

}
