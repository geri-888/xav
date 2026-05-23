package hu.xavpn.common;

public interface XavLogger {
    void info(String message);

    void warn(String message);

    void warn(String message, Throwable throwable);
}
