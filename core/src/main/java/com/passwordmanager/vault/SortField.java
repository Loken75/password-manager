package com.passwordmanager.vault;

public enum SortField {
    TITLE,
    USERNAME,
    EMAIL,
    URL,
    /** Modification date (updatedAt), most recent first. */
    DATE,
    /** Creation date (createdAt), most recent first. */
    CREATED,
    CATEGORY,
    FAVORITE,
    STRENGTH
}
