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
package com.moebiusgames.light.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServicePostMapping extends WebServiceMapping {

    private static final Pattern MULTIPART_BOUNDARY_PATTERN = Pattern.compile("boundary\\=(.+)", Pattern.DOTALL);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_PATTERN = Pattern.compile("^Content\\-Disposition\\:[\\s]+form-data.*", Pattern.DOTALL);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN = Pattern.compile("filename\\=\\\"(.*?)\\\"", Pattern.DOTALL);
    private static final Pattern MULTIPART_CONTENT_TYPE_PATTERN = Pattern.compile("^Content\\-Type\\:[\\s]+(.*)", Pattern.DOTALL);
    private static final ObjectReader OBJECT_READER = new ObjectMapper().registerModule(new JavaTimeModule()).reader();

    private Class<?> postParamType = null;
    private int postParamPos;

    public WebServicePostMapping(String pathPrefix, Method method) {
        super(HttpMethod.POST, pathPrefix, method);
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
        //if no post parameter is declared then we pass the parameter
    }

    @Override
    protected void addParameters(HttpServletRequest request, Object[] parameters) {
        if (this.postParamType != null) {
            final String contentType = request.getContentType().trim().toLowerCase();
            if (contentType.startsWith("application/json")) {
                handleJsonPost(request, parameters);
            } else if (contentType.startsWith("multipart/form-data")) {
                handleMultipartPost(request, parameters);
            } else {
                throw new IllegalArgumentException("Given post parameter was of type \"" + request.getContentType()
                        + "\" and not of type application/json or multipart/form-data");
            }
        }
    }

    private void handleJsonPost(HttpServletRequest request, Object[] parameters) throws IllegalArgumentException {
        try (Reader reader = request.getReader()) {
            Object postParameter = OBJECT_READER.forType(postParamType).readValue(reader);
            parameters[postParamPos] = postParameter;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Given post parameter could "
                    + "not be converted to type " + postParamType, ex);
        }
    }

    private void handleMultipartPost(HttpServletRequest request, Object[] parameters) throws IllegalArgumentException {
        if (postParamType != UploadedFile.class) {
            throw new IllegalStateException("Multipart upload but method does not accept a uploaded file");
        }
        final String contentType = request.getContentType().trim();
        final Matcher matcher = MULTIPART_BOUNDARY_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            throw new IllegalArgumentException("multipart not properly structured");
        }
        final String delimeter = matcher.group(1);
        if (delimeter.trim().isEmpty()) {
            throw new IllegalArgumentException("delimeter must not be blank");
        }

        String fileName = "unnamed";
        String fileContentType = "application/octet-stream";
        try (InputStream in = request.getInputStream()) {
            String line;
            while ((line = readLine(in)) != null
                    && !line.trim().isEmpty()) {
                if (MULTIPART_CONTENT_DISPOSITION_PATTERN.matcher(line).matches()) {
                    final Matcher fileNameMatcher = MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN.matcher(line);
                    if (fileNameMatcher.find()) {
                        fileName = fileNameMatcher.group(1);
                    }
                } else {
                    final Matcher contentTypeMatcher = MULTIPART_CONTENT_TYPE_PATTERN.matcher(line);
                    if (contentTypeMatcher.matches()) {
                        fileContentType = contentTypeMatcher.group(1);
                    }
                }
            }

            //now we read the content of the file until we meet the delimeter
            final File tmpFile = File.createTempFile("uploaded", ".dat");
            tmpFile.deleteOnExit(); //make sure we clean up our mess

            byte[] storedLine = null;
            try (FileOutputStream fOut = new FileOutputStream(tmpFile)) {
                byte[] rawLine;
                while ((rawLine = readRawLine(in)) != null) {
                    if (new String(rawLine).trim().equals("--" + delimeter)) {
                        if (storedLine != null) {
                            //remove CR & LF from last line as it is already part of the delimeter!
                            byte[] lastLine = Arrays.copyOf(storedLine, storedLine.length - 2);
                            fOut.write(lastLine);
                        }
                        break;
                    } else {
                        if (storedLine != null) {
                            fOut.write(storedLine);
                        }
                        storedLine = rawLine;
                    }
                }
            }

            if (tmpFile.length() == 0) {
                throw new IllegalArgumentException("No file uploaded");
            }
            parameters[postParamPos] = new UploadedFile(tmpFile, fileName, fileContentType);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Given post parameter could "
                    + "not be converted to type " + postParamType, ex);
        }
    }

    private static byte[] readRawLine(InputStream in) throws IOException {
        List<Byte> bytes = new ArrayList<>();

        int b;
        while ((b = in.read()) >= 0) {
            bytes.add((byte) b);
            if (b == '\n') break;
            if (b == -1 && bytes.isEmpty()) {
                return null; // end of stream
            }
        }

        byte[] data = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i) {
            data[i] = bytes.get(i);
        }

        return data;
    }

    private static String readLine(InputStream in) throws IOException {
        final byte[] rawLine = readRawLine(in);
        return rawLine != null
                ? new String(rawLine).trim()
                : null;
    }

}
