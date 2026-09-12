package com.atom.chat.nav;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NavigationStack<T> {
    private final List<T> pages = new ArrayList<>();

    public NavigationStack(T root) {
        pages.add(Objects.requireNonNull(root, "root"));
    }

    public void push(T page) {
        pages.add(Objects.requireNonNull(page, "page"));
    }

    public boolean pop() {
        if (pages.size() <= 1) {
            return false;
        }
        pages.remove(pages.size() - 1);
        return true;
    }

    public T peek() {
        return pages.get(pages.size() - 1);
    }

    public int size() {
        return pages.size();
    }

    public List<T> snapshot() {
        return List.copyOf(pages);
    }

    public void replaceWithRoot(T root) {
        Objects.requireNonNull(root, "root");
        pages.clear();
        pages.add(root);
    }
}
