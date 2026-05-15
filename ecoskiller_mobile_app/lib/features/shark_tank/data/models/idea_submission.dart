class IdeaSubmission {
  final String? id;
  final String title;
  final String problemStatement;
  final String solution;
  final String targetAudience;
  final String revenueModel;
  final String? prototypeLink;
  final List<String> teamMembers;
  final String mentorshipDomain;
  final String? videoUrl;
  final String status; // DRAFT, SUBMITTED, UNDER_REVIEW, SHORTLISTED, REJECTED, PITCHED, RESULT_DECLARED
  final DateTime? createdAt;

  IdeaSubmission({
    this.id,
    required this.title,
    required this.problemStatement,
    required this.solution,
    required this.targetAudience,
    required this.revenueModel,
    this.prototypeLink,
    required this.teamMembers,
    required this.mentorshipDomain,
    this.videoUrl,
    required this.status,
    this.createdAt,
  });

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'problemStatement': problemStatement,
      'solution': solution,
      'targetAudience': targetAudience,
      'revenueModel': revenueModel,
      'prototypeLink': prototypeLink,
      'teamMembers': teamMembers,
      'mentorshipDomain': mentorshipDomain,
      'videoUrl': videoUrl,
      'status': status,
      'createdAt': createdAt?.toIso8601String(),
    };
  }

  factory IdeaSubmission.fromJson(Map<String, dynamic> json) {
    return IdeaSubmission(
      id: json['id'],
      title: json['title'] ?? '',
      problemStatement: json['problemStatement'] ?? '',
      solution: json['solution'] ?? '',
      targetAudience: json['targetAudience'] ?? '',
      revenueModel: json['revenueModel'] ?? '',
      prototypeLink: json['prototypeLink'],
      teamMembers: List<String>.from(json['teamMembers'] ?? []),
      mentorshipDomain: json['mentorshipDomain'] ?? '',
      videoUrl: json['videoUrl'],
      status: json['status'] ?? 'DRAFT',
      createdAt: json['createdAt'] != null ? DateTime.parse(json['createdAt']) : null,
    );
  }
}
