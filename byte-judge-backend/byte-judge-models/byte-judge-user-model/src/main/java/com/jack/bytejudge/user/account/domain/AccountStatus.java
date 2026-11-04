package com.jack.bytejudge.user.account.domain;

/** Account lifecycle state, separate from the role; only a normal account may sign in. */
public enum AccountStatus {
    NORMAL("normal"),
    DISABLED("disabled");

    private final String storedName;

    AccountStatus(String storedName) {
        this.storedName = storedName;
    }

    public String storedName() {
        return storedName;
    }

    public boolean loginAllowed() {
        return this == NORMAL;
    }

    public static AccountStatus fromStored(String value) {
        for (AccountStatus status : values()) {
            if (status.storedName.equals(value)) {
                return status;
            }
        }
        throw new IllegalStateException("Unknown account status stored in the database: " + value);
    }
}
