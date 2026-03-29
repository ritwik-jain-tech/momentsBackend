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
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.moments.dao.UploadRecordDao;
import com.moments.models.UploadRecord;

@Repository
public class UploadRecordDaoImpl implements UploadRecordDao {

    private static final String COLLECTION = "uploadRecords";

    @Autowired
    private Firestore firestore;

    @Override
    public String create(UploadRecord record) throws ExecutionException, InterruptedException {
        long now = System.currentTimeMillis();
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        if (record.getUpdatedAt() == null) {
            record.setUpdatedAt(now);
        }
        DocumentReference ref = firestore.collection(COLLECTION).document();
        record.setUploadRecordId(null);
        ApiFuture<WriteResult> future = ref.set(record);
        future.get();
        return ref.getId();
    }

    @Override
    public void mergeFields(String id, Map<String, Object> fields) throws ExecutionException, InterruptedException {
        Map<String, Object> m = new HashMap<>(fields);
        m.put("updatedAt", System.currentTimeMillis());
        DocumentReference ref = firestore.collection(COLLECTION).document(id);
        ApiFuture<WriteResult> future = ref.set(m, SetOptions.merge());
        future.get();
    }

    @Override
    public List<UploadRecord> listByUserIdNewestFirst(String userId) throws ExecutionException, InterruptedException {
        if (userId == null || userId.isBlank()) {
            return new ArrayList<>();
        }
        CollectionReference col = firestore.collection(COLLECTION);
        Query q = col.whereEqualTo("userId", userId.trim()).orderBy("createdAt", Query.Direction.DESCENDING);
        ApiFuture<QuerySnapshot> future = q.get();
        List<UploadRecord> out = new ArrayList<>();
        for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
            UploadRecord r = doc.toObject(UploadRecord.class);
            if (r != null) {
                r.setUploadRecordId(doc.getId());
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public UploadRecord getById(String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return null;
        }
        DocumentSnapshot snap = firestore.collection(COLLECTION).document(id.trim()).get().get();
        if (!snap.exists()) {
            return null;
        }
        UploadRecord r = snap.toObject(UploadRecord.class);
        if (r != null) {
            r.setUploadRecordId(snap.getId());
        }
        return r;
    }
}
