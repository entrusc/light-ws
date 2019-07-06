/*
 * The MIT License
 *
 * Copyright 2019 Florian Frankenberger.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * The default multipart splitter that uses pure Java to split the multipart
 * body
 */
public class DefaultMultipartSplitter implements MultipartSplitter {

    @Override
    public List<MultipartSection> split(String boundary, File body) throws IOException {
        List<MultipartSection> sections = new LinkedList<>();
        MultipartSection currentSection = null;

        try (LineReaderInputStream in = new LineReaderInputStream(new FileInputStream(body))) {
            String line;
            int pos = 0;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(boundary)) {
                    if (currentSection != null) {
                        currentSection.setEnd(pos - 2); //CR + LF
                    }
                    if (!line.trim().equals(boundary + "--")) {
                        currentSection = new MultipartSection();
                        currentSection.setStart(pos + line.length());
                        sections.add(currentSection);
                    }
                }
                pos += line.length();
            }
        }

        return sections;
    }

}
