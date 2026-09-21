package io.github.ngirchev.fsm.spring.task;

@FunctionalInterface
public interface FsmTaskHandler<T> {

    void handle(T task);
}
