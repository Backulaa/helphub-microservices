package com.helphub.identity.user.dto;

public enum AgeGroup {
    YOUNG(15, 24),
    YOUNG_ADULT(25, 44),
    MIDDLE_AGE(45, 59),
    ELDERLY(60, 74),
    SENIOR(75, null);

    private final int minAge;
    private final Integer maxAge;

    AgeGroup(int minAge, Integer maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public int getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }
}