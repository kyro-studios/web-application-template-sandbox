package com.example.app.testing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.LoggerFactory;

/** 試験の中で、アプリケーションのログを集める。 */
public final class LogCapture implements AutoCloseable {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        appender.start();
        logger.addAppender(appender);
    }

    /** すべてのログを集める。 */
    public static LogCapture root() {
        return new LogCapture((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME));
    }

    /**
     * 条件に合うログが書かれるまで待ち、そのログを返す。要求のログは応答を返した後に書かれるため、試験ではこれで待つ。
     */
    public String await(Predicate<String> condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            for (String line : lines()) {
                if (condition.test(line)) {
                    return line;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("条件に合うログが書かれませんでした:" + System.lineSeparator() + text());
    }

    /** 集めたログの、整形したメッセージ (例外があればそのスタックトレースを含む) の一覧。 */
    public List<String> lines() {
        List<ILoggingEvent> events;
        synchronized (appender) {
            events = List.copyOf(appender.list);
        }
        return events.stream()
                .map(event -> event.getThrowableProxy() == null
                        ? event.getFormattedMessage()
                        : event.getFormattedMessage() + System.lineSeparator()
                                + ThrowableProxyUtil.asString(event.getThrowableProxy()))
                .toList();
    }

    /** 集めたログを 1 つの文字列にしたもの。 */
    public String text() {
        return String.join(System.lineSeparator(), lines());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
