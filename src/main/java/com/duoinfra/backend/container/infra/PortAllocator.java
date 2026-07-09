package com.duoinfra.backend.container.infra;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PortAllocator {

    private static final int PORT_START = 10000;
    private static final int PORT_END = 20000;

    private final AtomicInteger current = new AtomicInteger(PORT_START);

    public int allocate() {
        int port = current.getAndIncrement();
        if (port > PORT_END) {
            current.set(PORT_START);
            port = current.getAndIncrement();
        }
        return port;
    }
}
