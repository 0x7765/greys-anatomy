package com.github.ompc.greys.util.collection;

/**
 * 堆栈
 * Created by vlinux on 15/6/21.
 */
public interface GaStack<E> {

    E pop();

    void push(E e);

    E peek();

    boolean isEmpty();

}
