package com.moments.service;

import com.moments.dao.MomentDao;
import com.moments.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;

@Service
public class MomentService {

    @Autowired
    private MomentDao momentDao;

    // Create or Update a Moment
    public String saveMoment(Moment moment) throws ExecutionException, InterruptedException {
        moment.setUploadTime(Instant.now().toEpochMilli());
        if(moment.getCreationTime()==null){
            moment.setCreationTime(moment.getUploadTime());
        }
        moment.setStatus(MomentStatus.APPROVED);
        double aspectRatio = 0.4 + (Math.random() * 0.7);
        moment.setAspectRatio(Double.doubleToLongBits(aspectRatio));
        moment.setCreationTimeText(epocToString(moment.getCreationTime()));
        moment.setUploadTimeText(epocToString(moment.getUploadTime()));
        moment.setMomentId(generateMomentId(moment.getCreatorId()));
        return momentDao.saveMoment(moment);
    }


    public List<String> saveMoments(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> ids = new ArrayList<>();
        for (Moment moment : moments) {
            String id = saveMoment(moment); // Reuse the single save logic
            ids.add(id);
        }
        return ids;
    }

    public Boolean reportMoment(ReportRequest reportRequest) throws ExecutionException, InterruptedException {
       return momentDao.reportMoment(reportRequest);
    }

    private String generateMomentId(String creatorId) {
        return creatorId+"_"+epocToString(Instant.now().toEpochMilli());
    }

    // Get a Moment by ID
    public Moment getMomentById(String id) throws ExecutionException, InterruptedException {
        return momentDao.getMomentById(id);
    }

    // Get all Moments
    public List<Moment> getAllMoments() throws ExecutionException, InterruptedException {
        return momentDao.getAllMoments();
    }

    // Delete a Moment by ID
    public void deleteMoment(String id) throws ExecutionException, InterruptedException {
        momentDao.deleteMoment(id);
    }

    public MomentsResponse findMoments(String eventId, MomentFilter filter, Cursor cursor) throws ExecutionException, InterruptedException {
        int limit = cursor == null ? 20 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        String creatorId = filter == null ? null: filter.getCreatedById();
        String source = filter == null ? null: filter.getSource();
        List<Moment> moments = Objects.equals(source, "web") ?momentDao.getAllMoments(eventId)
                :momentDao.getMomentsFeed(creatorId, eventId, offset, limit);
        for (Moment moment : moments) {
            double aspectRatio = Math.round((0.4 + (Math.random() * 0.6)) * 100.0) / 100.0;
            moment.setAspectRatio(Double.doubleToLongBits(aspectRatio));
        }

        int totalCount = momentDao.getTotalCount(creatorId, eventId);
        boolean isLastPage= moments.size()<limit;
        Long  lastMomentCreationTime = moments.isEmpty() ? null : moments.get(moments.size()-1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset+moments.size(), limit,lastMomentCreationTime, isLastPage);
        return new MomentsResponse(moments, cursorOut);
    }

    private String epocToString(Long epoc){
        return Instant.ofEpochSecond(epoc/1000)
                .atZone(ZoneId.of("Asia/Kolkata")) // Use IST time zone
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd::HH:mm:ss"));
    }

    public String updateMomentStatus(String momentId, MomentStatus status) throws ExecutionException, InterruptedException {
        return momentDao.updateMomentStatus(momentId, status);
    }

}



