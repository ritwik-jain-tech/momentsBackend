package com.moments.dao.impl;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.OTPDao;
import com.moments.models.Moment;
import com.moments.models.OTPRequest;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

@Repository
public class OTPDaoImpl implements OTPDao {

    @Autowired
    private Firestore firestore;

private static final String COLLECTION_NAME = "otpCollection";

    @Override
    public void saveOtp(OTPRequest otpRequest) {
        firestore.collection(COLLECTION_NAME)
                .document(String.valueOf(otpRequest.getPhoneNumber()))
                .set(otpRequest);
    }

    @Override
    public OTPRequest getOtp(String phoneNumber) {
        try {
            return firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .get()
                    .get()
                    .getDocuments()
                    .stream()
                    .findFirst()
                    .map(doc -> doc.toObject(OTPRequest.class))
                    .orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error fetching OTP", e);
        }
    }

    @Override
    public void deleteOtp(String phoneNumber) {
        try {
            firestore.collection("otpCollection")
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .get()
                    .get()
                    .getDocuments()
                    .forEach(doc -> firestore.collection("otpCollection").document(doc.getId()).delete());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error deleting OTP", e);
        }
    }

    @Override
    public OTPRequest getOtpByPhoneNumber(String phoneNumber) {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(phoneNumber);
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = null;
        try {
            document = future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (document.exists()) {
            return document.toObject(OTPRequest.class);
        } else {
            return null;
        }
    }

}
