package com.jack.bytejudge.user.account.domain;

/** Role stored in the account table; the database check constraint admits exactly these values. */
public enum AccountRole {
    ADMIN("admin"),
    USER("user");

    private final String storedName;

    AccountRole(String storedName) {
        this.storedName = storedName;
    }

    public String storedName() {
        return storedName;
    }

    public static AccountRole fromStored(String value) {
        for (AccountRole role : values()) {
            if (role.storedName.equals(value)) {
                return role;
            }
        }
        throw new IllegalStateException("Unknown account role stored in the database: " + value);
    }
}
