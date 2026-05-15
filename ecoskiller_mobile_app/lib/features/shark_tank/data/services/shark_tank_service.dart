import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/idea_submission.dart';

class SharkTankService {
  // In a real app, this would call submission-svc, ai-evaluation-svc, etc.
  // For now, we use SharedPreferences to simulate a backend for demo purposes.

  Future<List<IdeaSubmission>> getMySubmissions() async {
    final prefs = await SharedPreferences.getInstance();
    final String? submissionsJson = prefs.getString('shark_tank_submissions');
    if (submissionsJson == null) return [];

    final List<dynamic> decoded = jsonDecode(submissionsJson);
    return decoded.map((item) => IdeaSubmission.fromJson(item)).toList();
  }

  Future<Map<String, dynamic>> submitIdea(IdeaSubmission submission) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      List<IdeaSubmission> current = await getMySubmissions();
      
      // Simulate ID generation
      final newSubmission = IdeaSubmission(
        id: submission.id ?? DateTime.now().millisecondsSinceEpoch.toString(),
        title: submission.title,
        problemStatement: submission.problemStatement,
        solution: submission.solution,
        targetAudience: submission.targetAudience,
        revenueModel: submission.revenueModel,
        prototypeLink: submission.prototypeLink,
        teamMembers: submission.teamMembers,
        mentorshipDomain: submission.mentorshipDomain,
        videoUrl: submission.videoUrl,
        status: submission.status,
        createdAt: DateTime.now(),
      );

      current.add(newSubmission);
      await prefs.setString('shark_tank_submissions', jsonEncode(current.map((e) => e.toJson()).toList()));

      return {"status": "success", "submission": newSubmission};
    } catch (e) {
      return {"status": "error", "message": e.toString()};
    }
  }

  Future<Map<String, dynamic>> getAiFeedback(String pitchText) async {
    // Simulate ai-evaluation-svc call
    await Future.delayed(const Duration(seconds: 2));
    return {
      "clarityScore": 85,
      "marketFeasibility": 70,
      "innovationScore": 90,
      "suggestions": [
        "Elaborate more on the revenue model.",
        "Include competitor analysis.",
        "Highlight the USP more clearly."
      ]
    };
  }
}
