package com.moments.dao;

import com.moments.models.TeamMember;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface TeamMemberDao {
    TeamMember saveTeamMember(TeamMember member) throws ExecutionException, InterruptedException;

    TeamMember getTeamMemberById(String memberId) throws ExecutionException, InterruptedException;

    /** All members for an agency, optionally filtered by role (null/blank = no role filter). */
    List<TeamMember> listByAgency(String agencyId, String role) throws ExecutionException, InterruptedException;

    void deleteTeamMember(String memberId) throws ExecutionException, InterruptedException;
}
