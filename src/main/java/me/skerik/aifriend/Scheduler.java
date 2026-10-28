package me.skerik.aifriend;

import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

/** The only way this plugin schedules work. Keeps the code ready for Folia's region threads. */
public interface Scheduler {

    /** Runs on the main thread (Folia: global region thread). Vault calls belong here. */
    void runGlobal(Runnable task);

    /** Runs on the thread owning the entity. {@code retired} runs instead if the entity is gone. */
    void runForEntity(Entity entity, Runnable task, @Nullable Runnable retired);

    void runAsync(Runnable task);

    void runLater(Runnable task, long ticks);

    /** Repeating global task; returns a handle to cancel it. */
    Runnable runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    Runnable runAsyncTimer(Runnable task, long delayTicks, long periodTicks);
}
