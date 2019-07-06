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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple input stream that allows to read until the next
 * new line character
 */
class LineReaderInputStream extends InputStream {

    private final InputStream in;
    private final byte[] buffer = new byte[4096];
    private int read = 0;
    private int pos = 0;

    public LineReaderInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        return nextByte();
    }

    private void fillBuffer() throws IOException {
        read = in.read(buffer);
        pos = 0;
    }

    public int nextByte() throws IOException {
        if (read == -1) {
            return -1;
        }
        if (pos == read) {
            fillBuffer();
        }
        return (((int) buffer[pos++]) & 0xFF);
    }

    public byte[] readRawLine() throws IOException {
        List<Byte> bytes = new ArrayList<>();

        int b;
        while ((b = nextByte()) != -1) {
            bytes.add((byte) b);
            if (b == '\n') {
                break;
            }
        }

        if (b == -1 && bytes.isEmpty()) {
            return null; // end of stream
        }

        byte[] data = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i) {
            data[i] = bytes.get(i);
        }

        return data;
    }

    public String readLine() throws IOException {
        final byte[] rawLine = readRawLine();
        return rawLine != null
                ? new String(rawLine)
                : null;
    }

}
