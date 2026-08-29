package com.example.tourismmedia.ui.trips;

/** Keeps trip ownership decisions consistent across detail and workspace screens. */
public final class TripPermissions {
    private TripPermissions() { }

    public static boolean canEdit(long ownerId, long currentUserId) {
        return currentUserId > 0 && ownerId == currentUserId;
    }
}
