package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.entity.InterviewSession;

import java.util.List;
import java.util.Map;

public interface AdminService {

	List<InterviewSession> getRecentSessions(String position, String difficulty, String language);


	Map<String, Object> getRecentSessionsPaginated(String position, String difficulty, String language, int page, int pageSize);


	Map<String, Object> getDashboardStats();


	boolean changePassword(String currentPassword, String newPassword);

}//AdminService
