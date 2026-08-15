package org.simonhareter.springinit.util;

public record DependencyRow(Dependency dependency, boolean isSelected, int originalIndex) implements DialogRow {

}
