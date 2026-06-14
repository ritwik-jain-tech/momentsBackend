package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.TeamMemberDao;
import com.moments.models.TeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class TeamMemberDaoImpl implements TeamMemberDao {

    private static final String COLLECTION_NAME = "teamMembers";
    private final Firestore db;

    @Autowired
    public TeamMemberDaoImpl(Firestore db) {
        this.db = db;
    }

    @Override
    public TeamMember saveTeamMember(TeamMember member) throws ExecutionException, InterruptedException {
        CollectionReference collection = db.collection(COLLECTION_NAME);
        DocumentReference docRef;
        if (member.getMemberId() == null || member.getMemberId().isEmpty()) {
            docRef = collection.document();
            member.setMemberId(docRef.getId());
        } else {
            docRef = collection.document(member.getMemberId());
        }
        ApiFuture<WriteResult> result = docRef.set(member);
        result.get();
        return member;
    }

    @Override
    public TeamMember getTeamMemberById(String memberId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(memberId);
        DocumentSnapshot document = docRef.get().get();
        return document.exists() ? document.toObject(TeamMember.class) : null;
    }

    @Override
    public List<TeamMember> listByAgency(String agencyId, String role) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME).whereEqualTo("agencyId", agencyId);
        if (role != null && !role.trim().isEmpty()) {
            query = query.whereEqualTo("role", role);
        }
        ApiFuture<QuerySnapshot> future = query.get();
        List<TeamMember> members = new ArrayList<>();
        for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
            members.add(doc.toObject(TeamMember.class));
        }
        return members;
    }

    @Override
    public void deleteTeamMember(String memberId) throws ExecutionException, InterruptedException {
        db.collection(COLLECTION_NAME).document(memberId).delete().get();
    }
}
