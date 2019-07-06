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
import com.moebiusgames.light.ws.MultipartSplitter.MultipartSection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServicePostMapping extends WebServiceMapping {

    private static final Pattern MULTIPART_BOUNDARY_PATTERN = Pattern.compile("boundary\\=(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_PATTERN = Pattern.compile("^Content\\-Disposition\\:[\\s]+form-data", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN = Pattern.compile("filename\\=\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_CONTENT_TYPE_PATTERN = Pattern.compile("^Content\\-Type\\:[\\s]+(.+)[\\r\\n]+?", Pattern.CASE_INSENSITIVE);

    private static final ObjectReader OBJECT_READER = new ObjectMapper().registerModule(new JavaTimeModule()).reader();

    private final MultipartSplitter multipartSplitter;

    private Class<?> postParamType = null;
    private int postParamPos;

    private boolean raw = false;

    public WebServicePostMapping(String pathPrefix, Method method,
            MultipartSplitter multipartSplitter) {
        super(HttpMethod.POST, pathPrefix, method);
        this.multipartSplitter = multipartSplitter;
        initPostParameter();
    }

    private void initPostParameter() {
        Method method = getMethod();
        final Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            final Parameter param = parameters[i];
            if (param.isAnnotationPresent(PostParameter.class)
                    || param.isAnnotationPresent(RawPostData.class)) {
                postParamPos = i;
                postParamType = param.getType();

                if (param.isAnnotationPresent(RawPostData.class)) {
                    if (postParamType != File.class) {
                        throw new IllegalStateException("Raw post parameter must be of type File");
                    }
                    raw = true;

                }
                return; //we take the first best post param (there shoud not be more than one!)
            }
        }
        //if no post parameter is declared then we pass the parameter
    }

    @Override
    protected void addParameters(HttpServletRequest request, Object[] parameters) {
        if (this.postParamType != null) {
            if (!raw) {
                final String contentType = request.getContentType().trim().toLowerCase();
                if (contentType.startsWith("application/json")) {
                    handleJsonPost(request, parameters);
                } else if (contentType.startsWith("multipart/form-data")) {
                    handleMultipartPost(request, parameters);
                } else {
                    throw new IllegalArgumentException("Given post parameter was of type \"" + request.getContentType()
                            + "\" and not of type application/json or multipart/form-data");
                }
            } else {
                handleRawPost(request, parameters);
            }
        }
    }

    private void handleRawPost(HttpServletRequest request, Object[] parameters) {
        try {
            final File tmpFile = File.createTempFile("raw", ".dat");
            tmpFile.deleteOnExit(); //make sure we clean up our mess

            try (InputStream in = request.getInputStream()) {
                Files.copy(in, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            parameters[this.postParamPos] = tmpFile;
        } catch (IOException e) {
            throw new IllegalStateException("Can't store raw post content", e);
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
        final String boundary = "--" + matcher.group(1);
        if (boundary.trim().isEmpty()) {
            throw new IllegalArgumentException("delimeter must not be blank");
        }

        try {
            File tmpFile = File.createTempFile("uploaded", ".dat");
            tmpFile.deleteOnExit();

            try (InputStream in = request.getInputStream()) {
                Files.copy(in, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // now parse the uploaded content for multipart boundaries
            List<MultipartSection> sections = multipartSplitter.split(boundary, tmpFile);
            UploadedFile uploadedFile = null;

            // extract sections to find the one with a file
            MultipartSection fileSection = null;
            String fileName = null;
            String fileContentType = null;
            int bytesOffset = 0;
            for (MultipartSection section : sections) {
                try (LineReaderInputStream in = new LineReaderInputStream(new FileInputStream(tmpFile))) {
                    in.skip(section.getStart());

                    String line;
                    bytesOffset = 0;
                    while ((line = in.readLine()) != null
                            && !line.trim().isEmpty()) {
                        if (MULTIPART_CONTENT_DISPOSITION_PATTERN.matcher(line).find()) {
                            final Matcher fileNameMatcher = MULTIPART_CONTENT_DISPOSITION_FILENAME_PATTERN.matcher(line);
                            if (fileNameMatcher.find()) {
                                fileName = fileNameMatcher.group(1);
                            }
                        } else {
                            final Matcher contentTypeMatcher = MULTIPART_CONTENT_TYPE_PATTERN.matcher(line);
                            if (contentTypeMatcher.find()) {
                                fileContentType = contentTypeMatcher.group(1);
                            }
                        }
                        bytesOffset += line.length();
                    }
                    bytesOffset += 2;

                    //found a file - mark it and break loop
                    if (fileContentType != null) {
                        fileSection = section;
                        break;
                    }
                }
            }

            //now actually extract the file
            if (fileSection != null) {
                try (FileInputStream in = new FileInputStream(tmpFile)) {
                    in.skip(fileSection.getStart() + bytesOffset);

                    byte[] buffer = new byte[4096];
                    int read;

                    File tmpPartFile = File.createTempFile("part", ".dat");
                    tmpPartFile.deleteOnExit();

                    int bytesLeft = fileSection.getEnd() - (fileSection.getStart() + bytesOffset);
                    try (FileOutputStream fOut = new FileOutputStream(tmpPartFile)) {
                        while (bytesLeft > 0
                                && (read = in.read(buffer, 0, Math.min(bytesLeft, buffer.length))) > -1) {
                            if (read > 0) {
                                fOut.write(buffer, 0, read);
                                bytesLeft -= read;
                            }
                        }
                    }
                    uploadedFile = new UploadedFile(tmpPartFile, fileName, contentType);
                }
            }

            if (uploadedFile == null) {
                throw new IllegalArgumentException("Could not find a file in uploaded multipart content");
            }
            parameters[this.postParamPos] = uploadedFile;

        } catch (IOException e) {
            throw new IllegalArgumentException("Could not decode multipart body", e);
        }

    }

}
