package com.moments.dao.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.moments.dao.MomentDao;
import com.moments.models.Moment;
import com.moments.models.MomentStatus;
import com.moments.models.ReportRequest;

@Repository
public class MomentDaoImpl implements MomentDao {

    @Autowired
    private Firestore firestore;

    private static final String COLLECTION_NAME = "moments";

    @Override
    public String saveMoment(Moment moment) throws ExecutionException, InterruptedException {

        DocumentReference documentReference = moment.getMomentId() == null || moment.getMomentId().isEmpty()
                ? firestore.collection(COLLECTION_NAME).document()
                : firestore.collection(COLLECTION_NAME).document(moment.getMomentId());

        if (moment.getMomentId() == null || moment.getMomentId().isEmpty()) {
            moment.setMomentId(documentReference.getId());
        }

        ApiFuture<WriteResult> future = documentReference.set(moment);
        future.get();

        return moment.getMomentId();
    }

    @Override
    public Moment getMomentById(String id) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.toObject(Moment.class);
        } else {
            throw new RuntimeException("Moment not found with ID: " + id);
        }
    }

    @Override
    public List<Moment> getAllMoments() throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> future = collection.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Moment> moments = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            moments.add(document.toObject(Moment.class));
        }
        return moments;
    }

    @Override
    public List<Moment> getAllMoments(String eventId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.orderBy("creationTime", Query.Direction.DESCENDING);
        if (eventId != null && !eventId.isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }
        // Fetch all documents matching the query
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Moment> moments = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            moments.add(documents.get(i).toObject(Moment.class));
        }

        return moments;
    }

    @Override
    public void deleteMoment(String id) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<WriteResult> future = documentReference.delete();
        future.get();
    }

    @Override
    public List<Moment> getMomentsFeed(String creatorUserId, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.orderBy("creationTime", Query.Direction.DESCENDING);

        query = query.whereEqualTo("status", "APPROVED");
        if (eventId != null && !eventId.isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }

        // Apply filter if creatorUserId is provided
        if (creatorUserId != null && !creatorUserId.isEmpty()) {
            query = query.whereEqualTo("creatorId", creatorUserId);
        }

        // Fetch all documents matching the query
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        // Apply pagination (offset and limit)
        List<Moment> moments = new ArrayList<>();
        int startIndex = Math.min(offset, documents.size());
        int endIndex = Math.min(startIndex + limit, documents.size());
        for (int i = startIndex; i < endIndex; i++) {
            moments.add(documents.get(i).toObject(Moment.class));
        }

        return moments;
    }

    @Override
    public List<Moment> getMomentsFeedByTaggedUser(String taggedUserId, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.orderBy("creationTime", Query.Direction.DESCENDING);

        query = query.whereEqualTo("status", "APPROVED");
        if (eventId != null && !eventId.isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }

        // Fetch all documents matching the query
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        // Filter by taggedUserId and collect all matching moments
        List<Moment> allMatchingMoments = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            Moment moment = document.toObject(Moment.class);
            // Check if taggedUserId is present in the moment's taggedUserIds list
            if (moment.getTaggedUserIds() != null && moment.getTaggedUserIds().contains(taggedUserId)) {
                allMatchingMoments.add(moment);
            }
        }

        // Apply pagination to the filtered results
        List<Moment> moments = new ArrayList<>();
        int startIndex = Math.min(offset, allMatchingMoments.size());
        int endIndex = Math.min(startIndex + limit, allMatchingMoments.size());

        for (int i = startIndex; i < endIndex; i++) {
            moments.add(allMatchingMoments.get(i));
        }

        return moments;
    }

    @Override
    public int getTotalCount(String creatorUserId, String eventId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection;

        if (eventId != null && !eventId.isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }

        // Apply filter if creatorUserId is provided
        if (creatorUserId != null && !creatorUserId.isEmpty()) {
            query = query.whereEqualTo("creatorId", creatorUserId);
        }
        // Count all matching documents
        ApiFuture<QuerySnapshot> future = query.get();
        return future.get().size();
    }

    @Override
    public int getTotalCountByTaggedUser(String taggedUserId, String eventId)
            throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection;

        if (eventId != null && !eventId.isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }

        // Fetch all documents matching the query
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        // Filter by taggedUserId and count
        int count = 0;
        for (QueryDocumentSnapshot document : documents) {
            Moment moment = document.toObject(Moment.class);
            // Check if taggedUserId is present in the moment's taggedUserIds list
            if (moment.getTaggedUserIds() != null && moment.getTaggedUserIds().contains(taggedUserId)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public boolean reportMoment(ReportRequest request) throws ExecutionException, InterruptedException {
        Moment moment = getMomentById(request.getMomentId());
        if (moment == null) {
            return false;
        }
        if (moment.getReportedBy() == null) {
            moment.setReportedBy(new ArrayList<>());
        }
        String report = request.getReportingUserId() + " : " + request.getEventId() + " : " + request.getReason();
        if (!moment.getReportedBy().contains(report)) {
            moment.getReportedBy().add(report);
            if (moment.getReportedBy().size() > 0) {
                moment.setStatus(MomentStatus.PENDING);
            }
            saveMoment(moment);
            return true;
        }
        return false;
    }

    @Override
    public String updateMomentStatus(String momentId, MomentStatus status)
            throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(momentId);
        DocumentSnapshot document = docRef.get().get();

        if (!document.exists()) {
            throw new RuntimeException("Moment not found with ID: " + momentId);
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        docRef.update(updates).get();
        return momentId;
    }

    @Override
    public List<Moment> getMomentsByIds(List<String> momentIds) throws ExecutionException, InterruptedException {
        if (momentIds == null || momentIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Create a map to store moments by their momentId for quick lookup
        Map<String, Moment> momentMap = new HashMap<>();
        CollectionReference collection = firestore.collection(COLLECTION_NAME);

        // Firestore doesn't support "IN" queries with more than 10 values
        // So we need to batch the requests
        int batchSize = 10;
        for (int i = 0; i < momentIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, momentIds.size());
            List<String> batch = momentIds.subList(i, endIndex);

            // Use "in" query for each batch
            Query query = collection.whereIn("momentId", batch);
            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                Moment moment = document.toObject(Moment.class);
                if (moment != null) {
                    momentMap.put(moment.getMomentId(), moment);
                }
            }
        }

        // Return moments in the same order as the requested momentIds
        List<Moment> orderedMoments = new ArrayList<>();
        for (String momentId : momentIds) {
            Moment moment = momentMap.get(momentId);
            if (moment != null) {
                orderedMoments.add(moment);
            }
        }

        return orderedMoments;
    }

    @Override
    public List<String> saveMomentsBatch(List<Moment> moments) throws ExecutionException, InterruptedException {
        if (moments == null || moments.isEmpty()) {
            return new ArrayList<>();
        }

        // Validate batch size - must be less than 50 moments
        if (moments.size() >= 50) {
            throw new IllegalArgumentException("Batch size must be less than 50 moments. Received: " + moments.size());
        }

        // Create a single batch operation
        WriteBatch batchWrite = firestore.batch();
        List<String> allIds = new ArrayList<>();

        for (Moment moment : moments) {
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(moment.getMomentId());
            batchWrite.set(docRef, moment);
            allIds.add(moment.getMomentId());
        }

        // Commit the batch
        ApiFuture<List<WriteResult>> future = batchWrite.commit();
        future.get(); // This will throw an exception if any operation in the batch fails

        return allIds;
    }

}
