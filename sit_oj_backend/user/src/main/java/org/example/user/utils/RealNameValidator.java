package org.example.user.utils;

public final class RealNameValidator {
    private RealNameValidator() {}

    public static boolean isValid(String realName) {
        return realName != null && !realName.isBlank()
                && realName.codePointCount(0, realName.length()) <= 50;
    }
}
