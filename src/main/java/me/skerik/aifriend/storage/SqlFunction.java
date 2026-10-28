package me.skerik.aifriend.storage;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T t) throws SQLException;
}
