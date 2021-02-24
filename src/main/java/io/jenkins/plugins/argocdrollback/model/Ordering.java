package io.jenkins.plugins.argocdrollback.model;

public enum Ordering {
    DESCENDING("New Versions First"),
    ASCENDING("Older Version First");

    public final String value;

    Ordering(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
