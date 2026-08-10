package dev.aether.holopanels.api;

public interface Registration extends AutoCloseable {
    @Override
    void close();

    boolean active();
}
