/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.local.store;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.ScheduledExecutorService;

import checkers.igj.quals.Immutable;
import checkers.igj.quals.ReadOnly;
import checkers.lock.quals.GuardedBy;
import com.google.common.base.Charsets;
import com.google.common.base.Ticker;
import com.google.common.io.CharSource;
import com.google.common.primitives.Longs;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.markers.NotThreadSafe;
import org.glowroot.markers.OnlyUsedByTests;
import org.glowroot.markers.ThreadSafe;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
public class RollingFile {

    private static final Logger logger = LoggerFactory.getLogger(RollingFile.class);

    private final File file;
    private final Object lock = new Object();
    @GuardedBy("lock")
    private final RollingOutputStream out;
    @GuardedBy("lock")
    private final Writer compressedWriter;
    private final Thread shutdownHookThread;
    @GuardedBy("lock")
    private RandomAccessFile inFile;
    private volatile boolean closing = false;

    RollingFile(File file, int requestedRollingSizeKb, ScheduledExecutorService scheduledExecutor,
            Ticker ticker) throws IOException {
        this.file = file;
        out = RollingOutputStream.create(file, requestedRollingSizeKb, scheduledExecutor, ticker);
        compressedWriter = new OutputStreamWriter(new LZFOutputStream(out), Charsets.UTF_8);
        inFile = new RandomAccessFile(file, "r");
        shutdownHookThread = new ShutdownHookThread();
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    }

    FileBlock write(@ReadOnly CharSource charSource) {
        synchronized (lock) {
            if (closing) {
                return FileBlock.expired();
            }
            out.startBlock();
            try {
                charSource.copyTo(compressedWriter);
                compressedWriter.flush();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                return FileBlock.expired();
            }
            return out.endBlock();
        }
    }

    @Immutable
    CharSource read(FileBlock block, String rolledOverResponse) {
        return new FileBlockCharSource(block, rolledOverResponse);
    }

    public void resize(int newRollingSizeKb) throws IOException {
        synchronized (lock) {
            if (closing) {
                return;
            }
            inFile.close();
            out.resize(newRollingSizeKb);
            inFile = new RandomAccessFile(file, "r");
        }
    }

    @OnlyUsedByTests
    void close() throws IOException {
        logger.debug("close()");
        synchronized (lock) {
            closing = true;
            out.close();
            inFile.close();
        }
        Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
    }

    @Immutable
    private class FileBlockCharSource extends CharSource {

        private final FileBlock block;
        private final String rolledOverResponse;

        private FileBlockCharSource(FileBlock block, String rolledOverResponse) {
            this.block = block;
            this.rolledOverResponse = rolledOverResponse;
        }

        @Override
        public Reader openStream() throws IOException {
            if (out.isRolledOver(block)) {
                return CharSource.wrap(rolledOverResponse).openStream();
            }
            // it's important to wrap FileBlockInputStream in a BufferedInputStream to prevent lots
            // of small reads from the underlying RandomAccessFile
            final int bufferSize = 32768;
            return new InputStreamReader(new LZFInputStream(new BufferedInputStream(
                    new FileBlockInputStream(block), bufferSize)), Charsets.UTF_8);
        }
    }

    @NotThreadSafe
    private class FileBlockInputStream extends InputStream {

        private final FileBlock block;
        private long blockIndex;

        private FileBlockInputStream(FileBlock block) {
            this.block = block;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            long blockRemaining = block.getLength() - blockIndex;
            if (blockRemaining == 0) {
                return -1;
            }
            synchronized (lock) {
                if (out.isRolledOver(block)) {
                    throw new IOException("Block rolled over mid-read");
                }
                long filePosition = out.convertToFilePosition(block.getStartIndex() + blockIndex);
                inFile.seek(RollingOutputStream.HEADER_SKIP_BYTES + filePosition);
                long fileRemaining = out.getRollingSizeKb() * 1024L - filePosition;
                int numToRead = (int) Longs.min(len, blockRemaining, fileRemaining);
                inFile.readFully(bytes, off, numToRead);
                blockIndex += numToRead;
                return numToRead;
            }
        }

        // delegate to read(...) above
        @Override
        public int read(byte[] bytes) throws IOException {
            return read(bytes, 0, bytes.length);
        }

        // delegate to read(...) above, though this should never get called since
        // FileBlockInputStream is wrapped in BufferedInputStream
        @Override
        public int read() throws IOException {
            logger.warn("read() performs very poorly, FileBlockInputStream should always be"
                    + " wrapped in BufferedInputStream");
            byte[] bytes = new byte[1];
            if (read(bytes, 0, 1) == -1) {
                return -1;
            } else {
                return bytes[0];
            }
        }
    }

    private class ShutdownHookThread extends Thread {
        @Override
        public void run() {
            try {
                // update flag outside of lock in case there is a backlog of threads already
                // waiting on the lock (once the flag is set, any threads in the backlog that
                // haven't acquired the lock will abort quickly once they do obtain the lock)
                closing = true;
                synchronized (lock) {
                    out.close();
                    inFile.close();
                }
            } catch (IOException e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
}
