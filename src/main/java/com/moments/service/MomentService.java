package com.moments.service;

import com.moments.dao.MomentDao;
import com.moments.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class MomentService {

    @Autowired
    private MomentDao momentDao; // Inject DAO

    // Create or Update a Moment
    public String saveMoment(Moment moment) throws ExecutionException, InterruptedException {
        moment.setUploadTime(Instant.now().toEpochMilli());
        if(moment.getCreationTime()==null){
            moment.setCreationTime(moment.getUploadTime());
        }
        moment.setStatus(MomentStatus.APPROVED);
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
        int limit = cursor == null ? 10 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        String creatorId = filter==null ? null: filter.getCreatedById();
        List<Moment> moments = momentDao.getMomentsFeed(creatorId, eventId, offset, limit);

        int totalCount = momentDao.getTotalCount(creatorId, eventId);
        Cursor cursorOut = new Cursor(totalCount, offset+moments.size(), limit, moments.get(moments.size()-1).getCreationTime());
        return new MomentsResponse(moments, cursorOut);
    }

    private String epocToString(Long epoc){
        return Instant.ofEpochSecond(epoc/1000)
                .atZone(ZoneId.of("Asia/Kolkata")) // Use IST time zone
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd::HH:mm:ss"));
    }

}



