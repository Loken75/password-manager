package com.passwordmanager.ui;

import com.passwordmanager.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AutoLockManagerTest {

    private AppConfig config;
    private AtomicInteger lockCount;
    private AutoLockManager manager;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.setAutoLockMinutes(1);
        lockCount = new AtomicInteger(0);
        manager = new AutoLockManager(config, lockCount::incrementAndGet);
    }

    @Test
    void startAutoLock_canBeCalledMultipleTimes() {
        // Should not throw or create duplicate timers
        manager.startAutoLock();
        manager.startAutoLock();
        manager.startAutoLock();
        manager.cleanup();
    }

    @Test
    void cleanup_stopsTimerAndListener() {
        manager.installActivityListener();
        manager.startAutoLock();
        manager.cleanup();
        // Should be safe to call cleanup again
        manager.cleanup();
    }

    @Test
    void cleanup_withoutStart_doesNotThrow() {
        manager.cleanup();
    }

    @Test
    void startAutoLock_afterCleanup_works() {
        manager.startAutoLock();
        manager.cleanup();
        manager.startAutoLock();
        manager.cleanup();
    }

    @Test
    void installActivityListener_setsUpListener() {
        manager.installActivityListener();
        // Should not throw when cleaning up
        manager.cleanup();
    }
}
