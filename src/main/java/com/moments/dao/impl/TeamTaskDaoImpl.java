package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.TeamTaskDao;
import com.moments.models.TeamTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class TeamTaskDaoImpl implements TeamTaskDao {

    private static final String COLLECTION_NAME = "teamTasks";
    private final Firestore db;

    @Autowired
    public TeamTaskDaoImpl(Firestore db) {
        this.db = db;
    }

    @Override
    public TeamTask saveTeamTask(TeamTask task) throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        DocumentReference docRef;
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            docRef = collection.document();
            task.setTaskId(docRef.getId());
        } else {
            docRef = collection.document(task.getTaskId());
        }
        ApiFuture<WriteResult> result = docRef.set(task);
        result.get();
        return task;
    }

    @Override
    public TeamTask getTeamTaskById(String taskId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(taskId);
        DocumentSnapshot document = docRef.get().get();
        return document.exists() ? document.toObject(TeamTask.class) : null;
    }

    @Override
    public List<TeamTask> listByAgency(String agencyId, String assigneeId, String status, String eventId)
            throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("agencyId", agencyId);
        if (assigneeId != null && !assigneeId.trim().isEmpty()) {
            query = query.whereEqualTo("assigneeId", assigneeId);
        }
        if (status != null && !status.trim().isEmpty()) {
            query = query.whereEqualTo("status", status);
        }
        if (eventId != null && !eventId.trim().isEmpty()) {
            query = query.whereEqualTo("eventId", eventId);
        }
        ApiFuture<QuerySnapshot> future = query.get();
        List<TeamTask> tasks = new ArrayList<>();
        for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
            tasks.add(doc.toObject(TeamTask.class));
        }
        return tasks;
    }

    @Override
    public void deleteTeamTask(String taskId) throws ExecutionException, InterruptedException {
        db.collection(COLLECTION_NAME).document(taskId).delete().get();
    }
}
