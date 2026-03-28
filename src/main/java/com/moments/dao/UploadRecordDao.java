package com.moments.dao;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.moments.models.UploadRecord;

public interface UploadRecordDao {

    String create(UploadRecord record) throws ExecutionException, InterruptedException;

    void mergeFields(String id, Map<String, Object> fields) throws ExecutionException, InterruptedException;

    List<UploadRecord> listByUserIdNewestFirst(String userId) throws ExecutionException, InterruptedException;
}
