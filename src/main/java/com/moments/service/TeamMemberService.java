package com.moments.service;

import com.moments.dao.TeamMemberDao;
import com.moments.models.TeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class TeamMemberService {

    @Autowired
    private TeamMemberDao teamMemberDao;

    /** Create a new member or update an existing one (when {@code memberId} is set). */
    public TeamMember createOrUpdateMember(TeamMember member) throws ExecutionException, InterruptedException {
        if (member.getStatus() == null || member.getStatus().trim().isEmpty()) {
            member.setStatus("ACTIVE");
        }
        if (member.getCreatedAt() == null) {
            member.setCreatedAt(System.currentTimeMillis());
        }
        return teamMemberDao.saveTeamMember(member);
    }

    public TeamMember getMember(String memberId) throws ExecutionException, InterruptedException {
        return teamMemberDao.getTeamMemberById(memberId);
    }

    public List<TeamMember> listMembers(String agencyId, String role) throws ExecutionException, InterruptedException {
        return teamMemberDao.listByAgency(agencyId, role);
    }

    public void deleteMember(String memberId) throws ExecutionException, InterruptedException {
        teamMemberDao.deleteTeamMember(memberId);
    }
}
