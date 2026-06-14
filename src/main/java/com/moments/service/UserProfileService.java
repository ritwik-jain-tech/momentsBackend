package com.moments.service;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moments.dao.EventDao;
import com.moments.dao.UserProfileDao;
import com.moments.models.Role;
import com.moments.models.UserProfile;
import com.moments.utils.IdentityUtils;

@Service
public class UserProfileService {

    private final UserProfileDao userProfileDao;
    private final EventDao eventDao;

    @Autowired
    private EventRoleService eventRoleService;

    @Autowired
    public UserProfileService(UserProfileDao userProfileDao, EventDao eventDao) {
        this.userProfileDao = userProfileDao;
        this.eventDao = eventDao;
    }

    public Long createUser(UserProfile userProfile) throws ExecutionException, InterruptedException {
        return userProfileDao.createUserProfile(userProfile);
    }

    /**
     * Studio / Firebase onboarding: creates a profile without requiring an event
     * id.
     */
    public UserProfile createMinimalStudioUser(UserProfile seed) throws ExecutionException, InterruptedException {
        if (seed.getEventIds() == null) {
            seed.setEventIds(new ArrayList<>());
        }
        if (seed.getBlockedUserIds() == null) {
            seed.setBlockedUserIds(new ArrayList<>());
        }
        if (seed.getRole() == null) {
            seed.setRole(Role.PHOTOGRAPHER);
        }
        userProfileDao.createUserProfile(seed);
        return getUser(seed.getUserId());
    }

    /**
     * Single source of truth for "one human = one profile". Resolves an existing
     * {@link UserProfile} by, in order, firebaseUid → email → phone, backfilling any
     * missing identity keys on the match; creates a minimal studio profile when none
     * is found. Used by every auth entry point (Google/Firebase, OTP) so the same
     * person never ends up as duplicate profiles. Inputs are normalized here.
     */
    public UserProfile resolveOrCreateProfile(String firebaseUid, String email, String phone, String name)
            throws ExecutionException, InterruptedException {
        String emailLower = IdentityUtils.normalizeEmail(email);
        String phone10 = IdentityUtils.normalizeTenDigitPhone(phone);

        UserProfile profile = null;
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            profile = userProfileDao.findByFirebaseUid(firebaseUid);
        }
        if (profile == null && emailLower != null) {
            profile = userProfileDao.findByEmailId(emailLower);
        }
        if (profile == null && phone10 != null) {
            profile = userProfileDao.findByPhoneNumber(phone10);
        }

        if (profile == null) {
            UserProfile seed = new UserProfile();
            seed.setFirebaseUid(firebaseUid);
            seed.setEmailId(emailLower);
            seed.setPhoneNumber(phone10);
            if (name != null && !name.isBlank()) {
                seed.setName(name);
            }
            return createMinimalStudioUser(seed);
        }

        if (backfillIdentity(profile, firebaseUid, emailLower, phone10, name)) {
            userProfileDao.updateUserProfile(profile);
        }
        return getUser(profile.getUserId());
    }

    /** Fills in any identity key the resolved profile is missing. Returns true if changed. */
    private static boolean backfillIdentity(UserProfile profile, String firebaseUid, String emailLower,
            String phone10, String name) {
        boolean changed = false;
        if (firebaseUid != null && !firebaseUid.isBlank() && !firebaseUid.equals(profile.getFirebaseUid())) {
            profile.setFirebaseUid(firebaseUid);
            changed = true;
        }
        if (emailLower != null && (profile.getEmailId() == null || profile.getEmailId().isBlank())) {
            profile.setEmailId(emailLower);
            changed = true;
        }
        if (phone10 != null && (profile.getPhoneNumber() == null || profile.getPhoneNumber().isBlank())) {
            profile.setPhoneNumber(phone10);
            changed = true;
        }
        if (name != null && !name.isBlank() && (profile.getName() == null || profile.getName().isBlank())) {
            profile.setName(name);
            changed = true;
        }
        return changed;
    }

    public UserProfile getUserProfileByFirebaseUid(String firebaseUid) {
        return userProfileDao.findByFirebaseUid(firebaseUid);
    }

    public UserProfile getUserProfileByEmailId(String emailIdLowercase) {
        if (emailIdLowercase == null) {
            return null;
        }
        return userProfileDao.findByEmailId(emailIdLowercase.toLowerCase());
    }

    public UserProfile getUser(String userId) throws ExecutionException, InterruptedException {
        UserProfile userProfile = userProfileDao.getUserProfile(userId);
        if (userProfile == null) {
            return null;
        }
        if (userProfile.getEventIds() == null) {
            userProfile.setEventIds(new ArrayList<>());
        }
        userProfile.setEventDetails(eventDao.getEventsByIds(userProfile.getEventIds()));
        return userProfile;
    }

    public void updateUser(UserProfile userProfile) throws ExecutionException, InterruptedException {
        userProfileDao.updateUserProfile(userProfile);
    }

    public void deleteUser(String userId) throws ExecutionException, InterruptedException {
        userProfileDao.deleteUserProfile(userId);
    }

    public UserProfile getUserProfileByPhoneNumber(String phoneNumber) throws ExecutionException, InterruptedException {
        UserProfile userProfile = userProfileDao.findByPhoneNumber(phoneNumber);
        return userProfile;
    }

    public UserProfile addUserToEvent(String userId, String eventId, Boolean isGroomSide)
            throws ExecutionException, InterruptedException {
        return addUserToEvent(userId, eventId, isGroomSide, null);
    }

    public UserProfile addUserToEvent(String userId, String eventId, Boolean isGroomSide, String roleName)
            throws ExecutionException, InterruptedException {
        UserProfile userProfile = userProfileDao.addUserToEvent(userId, eventId);
        eventDao.addUserToEvent(eventId, userId, isGroomSide);
        // Create EventRole for this user and event
        eventRoleService.createOrUpdateEventRole(eventId, userId, roleName);
        return userProfile;
    }

    public void blockUser(String blockingUserId, String blockedUserId) throws ExecutionException, InterruptedException {
        userProfileDao.blockUser(blockingUserId, blockedUserId);
    }

    public void unblockUser(String unblockingUserId, String blockedUserId)
            throws ExecutionException, InterruptedException {
        userProfileDao.unblockUser(unblockingUserId, blockedUserId);
    }

    public void deleteUserByPhoneNumber(String phoneNumber) throws ExecutionException, InterruptedException {
        UserProfile userProfile = getUserProfileByPhoneNumber(phoneNumber);
        if (userProfile != null) {
            deleteUser(userProfile.getUserId());
        }
    }
}
