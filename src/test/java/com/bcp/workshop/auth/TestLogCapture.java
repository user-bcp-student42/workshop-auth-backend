package com.bcp.workshop.auth;

import org.jboss.logmanager.LogManager;
import org.jboss.logmanager.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

final class TestLogCapture implements AutoCloseable {
    private final Logger logger;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    TestLogCapture(Class<?> category) {
        logger = (Logger) LogManager.getLogManager().getLogger(category.getName());
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    String output() {
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        for (LogRecord record : records) {
            writer.println(record.getMessage());
            if (record.getThrown() != null) {
                record.getThrown().printStackTrace(writer);
            }
        }
        writer.flush();
        return output.toString();
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        handler.close();
    }
}